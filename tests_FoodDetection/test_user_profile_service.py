"""
test_user_profile_service.py – Unit tests cho UserProfileService.

Yêu cầu:
  - SQLite in-memory + rollback (db_session fixture)
  - Mock external dependency: app.external.auth_service.fetch_user_profile
  - Không gọi AuthService thật / không gọi external API

Test cases:
  TC-UP-01: get_user_profile – không tồn tại → 404 (Negative)
  TC-UP-02: create_user_profile – thành công → 201 + tạo UserProfile + DailyEnergyLog (Positive/DB)
  TC-UP-03: create_user_profile – profile đã tồn tại → 400 (Negative)
  TC-UP-04: create_user_profile – auth_service lỗi → 502 (Exception)
  TC-UP-05: update_user_profile – chưa có profile → fallback create → 201 (Positive)
  TC-UP-06: update_user_profile – có profile, update weight → tạo thêm weight history (DB)
  TC-UP-07: create_user_profile – payload thiếu field bắt buộc → KeyError (Exception)
  TC-UP-08: get_user_profile – tồn tại → 200 + JSON đúng user_id
  TC-UP-09: get_weight_history – không có profile → 404
  TC-UP-10: get_weight_history – có dữ liệu → comment BMI
  TC-UP-11: update_user_profile – date_of_birth sai format → ValueError
  TC-UP-12: build_ai_profile_input – thiếu weight history → lỗi
"""

import json
from datetime import date

import pytest

from app.models.user_profile import UserProfile
from app.models.user_profile_weight_history import UserProfileWeightHistory
from app.models.daily_energy_log import DailyEnergyLog
from app.enums.app_enum import ActivityLevelEnum
from app.services.user_profile_service import UserProfileService


def _mock_auth_user_info():
    return {"gender": "male", "dateOfBirth": "2000-01-01"}


def test_TC_UP_01_get_user_profile_not_found_returns_404(db_session):
    """
    TC-UP-01: get_user_profile – không có profile → 404.
    CheckDB: query UserProfile = None
    """
    # Act
    resp, status = UserProfileService.get_user_profile(user_id=99999)

    # Assert
    assert status == 404
    data = json.loads(resp.data)
    assert "Profile not found" in data["error"]


def test_TC_UP_02_create_user_profile_success_creates_profile_and_daily_log(db_session, mocker):
    """
    TC-UP-02: create_user_profile thành công → 201, tạo UserProfile + (optional) WeightHistory + DailyLog hôm nay.
    CheckDB: DB có row mới; DailyEnergyLog được upsert
    Rollback: db_session fixture
    """
    # Arrange
    user_id = 1001
    payload = {
        "activity_level": ActivityLevelEnum.sedentary,
        "aim_weight": 70.0,
        "aim_day": "2026-02-01",
        "aim_day_end": "2026-03-01",
        "day_of_activities": 3,
        "height_cm": 175.0,
        "weight_kg": 70.0,
    }

    mocker.patch("app.services.user_profile_service.fetch_user_profile", return_value=_mock_auth_user_info())
    # upsert_today_daily_log -> calculate_tdee cần tuple (bmr,tdee,target)
    mocker.patch("app.services.user_profile_service.calculate_tdee", return_value=(1600, 2000, 2000))

    # Act
    resp, status = UserProfileService.create_user_profile(user_id, payload, jwt_token="dummy")

    # Assert HTTP
    assert status == 201
    data = json.loads(resp.data)
    assert data["user_id"] == user_id
    assert data["activity_level"] == ActivityLevelEnum.sedentary.value

    # CheckDB: profile created
    profile = UserProfile.query.filter_by(user_id=user_id).first()
    assert profile is not None

    # CheckDB: weight history created
    wh = UserProfileWeightHistory.query.filter_by(user_profile_id=profile.id).all()
    assert len(wh) == 1
    assert wh[0].height_cm == 175.0
    assert wh[0].weight_kg == 70.0

    # CheckDB: daily log today created
    log = DailyEnergyLog.query.filter_by(user_id=user_id, log_date=date.today()).first()
    assert log is not None
    assert log.target_calorie == 2000


def test_TC_UP_03_create_user_profile_already_exists_returns_400(db_session, mocker):
    """
    TC-UP-03: create_user_profile – profile đã tồn tại → 400.
    """
    # Arrange
    user_id = 1002
    db_session.add(
        UserProfile(
            user_id=user_id,
            gender="male",
            activity_level=ActivityLevelEnum.sedentary,
            aim_weight=70.0,
            date_of_birth=date(2000, 1, 1),
        )
    )
    db_session.flush()

    payload = {"activity_level": ActivityLevelEnum.sedentary, "aim_weight": 70.0}

    # Act
    resp, status = UserProfileService.create_user_profile(user_id, payload, jwt_token="dummy")

    # Assert
    assert status == 400
    data = json.loads(resp.data)
    assert "Profile already exists" in data["error"]


def test_TC_UP_04_create_user_profile_auth_service_error_returns_502(db_session, mocker):
    """
    TC-UP-04: fetch_user_profile raise Exception → 502.
    """
    # Arrange
    user_id = 1003
    payload = {"activity_level": ActivityLevelEnum.sedentary, "aim_weight": 70.0}
    mocker.patch("app.services.user_profile_service.fetch_user_profile", side_effect=Exception("Auth down"))

    # Act
    resp, status = UserProfileService.create_user_profile(user_id, payload, jwt_token="dummy")

    # Assert
    assert status == 502
    data = json.loads(resp.data)
    assert "Auth down" in data["error"]


def test_TC_UP_05_update_user_profile_when_missing_profile_falls_back_to_create(db_session, mocker):
    """
    TC-UP-05: update_user_profile — chưa có profile → gọi create_user_profile → 201.
    CheckDB: tạo profile mới
    """
    # Arrange
    user_id = 1004
    payload = {"activity_level": ActivityLevelEnum.sedentary, "aim_weight": 70.0}
    mocker.patch("app.services.user_profile_service.fetch_user_profile", return_value=_mock_auth_user_info())
    mocker.patch("app.services.user_profile_service.calculate_tdee", return_value=(1600, 2000, 2000))

    # Act
    resp, status = UserProfileService.update_user_profile(user_id, payload, jwt_token="dummy")

    # Assert
    assert status == 201
    profile = UserProfile.query.filter_by(user_id=user_id).first()
    assert profile is not None


def test_TC_UP_06_update_user_profile_weight_changed_creates_new_history(db_session, mocker):
    """
    TC-UP-06: update_user_profile — có profile, weight changed → thêm weight history mới.
    Arrange: có profile + 1 history, update weight -> 2 histories
    CheckDB: histories count tăng
    Rollback: db_session
    """
    # Arrange
    user_id = 1005
    profile = UserProfile(
        user_id=user_id,
        gender="male",
        activity_level=ActivityLevelEnum.sedentary,
        aim_weight=70.0,
        date_of_birth=date(2000, 1, 1),
    )
    db_session.add(profile)
    db_session.flush()
    db_session.add(
        UserProfileWeightHistory(
            user_profile_id=profile.id,
            height_cm=175.0,
            weight_kg=70.0,
            bmi=22.86,
        )
    )
    db_session.flush()

    mocker.patch("app.services.user_profile_service.calculate_tdee", return_value=(1600, 2000, 2000))

    payload = {
        "weight_kg": 72.0,  # changed
        "activity_level": ActivityLevelEnum.sedentary,
        "aim_weight": 70.0,
    }

    # Act
    resp, status = UserProfileService.update_user_profile(user_id, payload, jwt_token="dummy")

    # Assert
    assert status == 200
    histories = (
        UserProfileWeightHistory.query
        .filter_by(user_profile_id=profile.id)
        .order_by(UserProfileWeightHistory.created_at.asc())
        .all()
    )
    assert len(histories) == 2
    assert histories[-1].weight_kg == 72.0


def test_TC_UP_07_create_user_profile_missing_required_field_raises_keyerror(db_session, mocker):
    """
    TC-UP-07: payload thiếu key bắt buộc (activity_level) → KeyError (Exception).
    """
    user_id = 1006
    payload = {"aim_weight": 70.0}  # thiếu activity_level
    mocker.patch("app.services.user_profile_service.fetch_user_profile", return_value=_mock_auth_user_info())

    with pytest.raises(KeyError):
        UserProfileService.create_user_profile(user_id, payload, jwt_token="dummy")


def test_TC_UP_08_get_user_profile_success_returns_200(db_session):
    """
    TC-UP-08: get_user_profile — profile tồn tại → 200, user_id khớp.
    CheckDB: đọc DB; Rollback: fixture
    """
    user_id = 1007
    db_session.add(
        UserProfile(
            user_id=user_id,
            gender="female",
            activity_level=ActivityLevelEnum.sedentary,
            aim_weight=55.0,
            date_of_birth=date(1999, 5, 5),
        )
    )
    db_session.flush()

    resp, status = UserProfileService.get_user_profile(user_id)
    assert status == 200
    data = json.loads(resp.data)
    assert data["user_id"] == user_id
    assert data["gender"] == "female"


def test_TC_UP_09_get_weight_history_profile_not_found_returns_404(db_session):
    """
    TC-UP-09: get_weight_history — không có profile → 404.
    """
    resp, status = UserProfileService.get_weight_history(99998)
    assert status == 404
    assert "Profile not found" in json.loads(resp.data)["error"]


def test_TC_UP_10_get_weight_history_returns_bmi_comments(db_session):
    """
    TC-UP-10: get_weight_history — có 2 bản ghi → comment Underweight/Normal theo BMI.
    CheckDB: Yes; Rollback: fixture
    """
    user_id = 1008
    profile = UserProfile(
        user_id=user_id,
        gender="male",
        activity_level=ActivityLevelEnum.sedentary,
        aim_weight=70.0,
        date_of_birth=date(2000, 1, 1),
    )
    db_session.add(profile)
    db_session.flush()
    db_session.add(
        UserProfileWeightHistory(
            user_profile_id=profile.id,
            height_cm=180.0,
            weight_kg=50.0,
            bmi=15.4,
        )
    )
    db_session.add(
        UserProfileWeightHistory(
            user_profile_id=profile.id,
            height_cm=180.0,
            weight_kg=75.0,
            bmi=23.1,
        )
    )
    db_session.flush()

    resp, status = UserProfileService.get_weight_history(user_id)
    assert status == 200
    data = json.loads(resp.data)
    comments = [row["comment"] for row in data["weight_history"]]
    assert "Underweight" in comments
    assert "Normal" in comments


def test_TC_UP_11_update_user_profile_invalid_date_of_birth_raises(db_session, mocker):
    """
    TC-UP-11: update_user_profile — date_of_birth không phải ISO date → ValueError.
    """
    user_id = 1009
    db_session.add(
        UserProfile(
            user_id=user_id,
            gender="male",
            activity_level=ActivityLevelEnum.sedentary,
            aim_weight=70.0,
            date_of_birth=date(2000, 1, 1),
        )
    )
    db_session.flush()
    mocker.patch("app.services.user_profile_service.calculate_tdee", return_value=(1600, 2000, 2000))

    payload = {"date_of_birth": "not-a-valid-date"}

    with pytest.raises(ValueError):
        UserProfileService.update_user_profile(user_id, payload, jwt_token="dummy")


def test_TC_UP_12_build_ai_profile_input_missing_weight_history_returns_error(db_session):
    """
    TC-UP-12: build_ai_profile_input — có profile nhưng không có weight history → (None, error).
    CheckDB: Yes; Rollback: fixture
    """
    user_id = 1010
    profile = UserProfile(
        user_id=user_id,
        gender="male",
        activity_level=ActivityLevelEnum.sedentary,
        aim_weight=70.0,
        date_of_birth=date(2000, 1, 1),
    )
    db_session.add(profile)
    db_session.flush()

    data, err = UserProfileService.build_ai_profile_input(user_id)
    assert data is None
    assert err == "Weight/height history not found"

