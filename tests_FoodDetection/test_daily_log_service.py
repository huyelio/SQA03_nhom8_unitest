"""
test_daily_log_service.py – Unit tests cho daily_log_service.

Test cases:
  TC-DL-01: calculate_bmr_from_metrics – nam 25 tuổi → BMR = 1668
  TC-DL-02: calculate_bmr_from_metrics – nữ 30 tuổi → BMR = 1239
  TC-DL-03: calculate_bmr_from_metrics – thiếu dữ liệu → trả 0
  TC-DL-04: DailyLogService.update_daily_steps – cập nhật thành công → CheckDB
  TC-DL-05: DailyLogService.update_daily_steps – user không có profile → lỗi
"""

import pytest
from datetime import date, timedelta

from app.extensions import db
from app.models.user_profile import UserProfile
from app.models.user_profile_weight_history import UserProfileWeightHistory
from app.models.daily_energy_log import DailyEnergyLog
from app.enums.app_enum import ActivityLevelEnum
from app.services.daily_log_service import calculate_bmr_from_metrics, DailyLogService


# ===========================================================================
# Helpers tạo test data
# ===========================================================================

def _create_full_user(user_id: int, weight_kg: float, height_cm: float, session) -> tuple:
    """Tạo UserProfile + WeightHistory + DailyEnergyLog cho ngày hôm nay."""
    profile = UserProfile(
        user_id=user_id,
        gender="male",
        date_of_birth=date(2001, 1, 1),  # ~25 tuổi
        activity_level=ActivityLevelEnum.sedentary,
        aim_weight=70.0,
    )
    session.add(profile)
    session.flush()

    weight_history = UserProfileWeightHistory(
        user_profile_id=profile.id,
        height_cm=height_cm,
        weight_kg=weight_kg,
        bmi=round(weight_kg / ((height_cm / 100) ** 2), 2),
    )
    session.add(weight_history)
    session.flush()

    log = DailyEnergyLog(
        user_id=user_id,
        log_date=date.today(),
        total_calorie_in=0,
        total_steps=0,
        steps_calorie_out=0,
        tdee=2000,
        target_calorie=2000,
    )
    session.add(log)
    session.flush()

    return profile, weight_history, log


# ===========================================================================
# TC-DL-01 đến TC-DL-03: calculate_bmr_from_metrics (thuần toán, không cần DB)
# ===========================================================================

class TestCalculateBmrFromMetrics:

    def test_TC_DL_01_male_25yo_returns_correct_bmr(self):
        """
        TC-DL-01: Nam 25 tuổi, 70kg, 175cm → BMR theo Mifflin-St Jeor.
        Input    : height=175, weight=70, gender="male", dob tương ứng 25 tuổi
        Expected : BMR = int(10*70 + 6.25*175 - 5*25 + 5) = int(700+1093.75-125+5) = 1673
        Ghi chú  : Giá trị thực phụ thuộc vào ngày hôm nay (tuổi tính theo ngày sinh)
        """
        # Tính tuổi chính xác 25 năm trước hôm nay
        today = date.today()
        dob = date(today.year - 25, today.month, today.day)

        # Act
        bmr = calculate_bmr_from_metrics(175, 70, "male", dob)

        # Assert: BMR = int(10*70 + 6.25*175 - 5*25 + 5) = int(1673.75) = 1673
        expected_bmr = int(10 * 70 + 6.25 * 175 - 5 * 25 + 5)
        assert bmr == expected_bmr, f"BMR expected {expected_bmr}, got {bmr}"

    def test_TC_DL_02_female_30yo_returns_correct_bmr(self):
        """
        TC-DL-02: Nữ 30 tuổi, 55kg, 160cm → BMR theo Mifflin-St Jeor.
        Input    : height=160, weight=55, gender="female", dob tương ứng 30 tuổi
        Expected : BMR = int(10*55 + 6.25*160 - 5*30 - 161) = int(550+1000-150-161) = 1239
        """
        today = date.today()
        dob = date(today.year - 30, today.month, today.day)

        # Act
        bmr = calculate_bmr_from_metrics(160, 55, "female", dob)

        # Assert
        expected_bmr = int(10 * 55 + 6.25 * 160 - 5 * 30 - 161)
        assert bmr == expected_bmr, f"BMR expected {expected_bmr}, got {bmr}"

    def test_TC_DL_03_missing_data_returns_zero(self):
        """
        TC-DL-03: Thiếu dữ liệu (height=None) → trả 0.
        Input    : height=None, weight=70, gender="male", dob hợp lệ
        Expected : 0 (không đủ dữ liệu để tính)
        """
        dob = date(2001, 1, 1)

        # Act
        bmr = calculate_bmr_from_metrics(None, 70, "male", dob)

        # Assert
        assert bmr == 0, f"Khi height=None phải trả 0, got {bmr}"


# ===========================================================================
# TC-DL-04 đến TC-DL-05: DailyLogService.update_daily_steps
# ===========================================================================

class TestUpdateDailySteps:

    def test_TC_DL_04_update_steps_success_updates_db(self, db_session):
        """
        TC-DL-04: Cập nhật steps thành công → total_steps và steps_calorie_out đúng trong DB.
        Input    : user_id=501 có profile + weight 70kg, steps=5000
        Expected : step_calorie = int(5000 * 0.0005 * 70) = 175
        CheckDB  : total_steps=5000, steps_calorie_out=175 trong DailyEnergyLog
        Rollback : db_session fixture tự động rollback
        """
        user_id = 501
        weight_kg = 70.0

        # Chuẩn bị: tạo user đầy đủ với log hôm nay
        _create_full_user(user_id, weight_kg, 175.0, db_session)

        # Act
        result, error = DailyLogService.update_daily_steps(
            user_id=user_id,
            steps=5000,
            log_date=date.today().isoformat()
        )

        # Assert
        assert error is None, f"Không mong đợi error, got: {error}"
        assert result is not None

        expected_step_calorie = int(5000 * 0.0005 * weight_kg)  # = 175
        assert result["total_steps"] == 5000
        assert result["step_calorie"] == expected_step_calorie

        # CheckDB: kiểm tra trực tiếp trong DB
        log = DailyEnergyLog.query.filter_by(
            user_id=user_id, log_date=date.today()
        ).first()
        assert log is not None
        assert log.total_steps == 5000
        assert log.steps_calorie_out == expected_step_calorie

    def test_TC_DL_05_update_steps_no_profile_returns_error(self, db_session):
        """
        TC-DL-05: User không có UserProfile → trả (None, error message).
        Input    : user_id=999 không có profile trong DB
        Expected : (None, "User profile not found")
        """
        user_id = 999  # User không tồn tại trong DB

        # Act
        result, error = DailyLogService.update_daily_steps(
            user_id=user_id,
            steps=3000
        )

        # Assert
        assert result is None
        assert error == "User profile not found"

    def test_TC_DL_06_update_steps_no_weight_history_returns_error(self, db_session):
        """
        TC-DL-06: Có profile nhưng không có weight history → (None, 'User weight not found').
        CheckDB  : profile tồn tại, nhưng weight history không có
        """
        # Arrange: tạo profile nhưng không tạo weight history
        profile = UserProfile(
            user_id=777,
            gender="male",
            activity_level=ActivityLevelEnum.sedentary,
            aim_weight=70.0,
            date_of_birth=date(2000, 1, 1),
        )
        db_session.add(profile)
        db_session.flush()

        # Act
        result, error = DailyLogService.update_daily_steps(user_id=777, steps=1234)

        # Assert
        assert result is None
        assert error == "User weight not found"

    def test_TC_DL_07_update_steps_daily_log_not_found_returns_error(self, db_session):
        """
        TC-DL-07: Có profile + weight, nhưng không có DailyEnergyLog cho ngày đó → (None, 'Daily log not found').
        """
        # Arrange: tạo profile + weight history, KHÔNG tạo DailyEnergyLog
        profile = UserProfile(
            user_id=778,
            gender="male",
            activity_level=ActivityLevelEnum.sedentary,
            aim_weight=70.0,
            date_of_birth=date(2000, 1, 1),
        )
        db_session.add(profile)
        db_session.flush()

        wh = UserProfileWeightHistory(
            user_profile_id=profile.id,
            height_cm=175.0,
            weight_kg=70.0,
            bmi=22.86,
        )
        db_session.add(wh)
        db_session.flush()

        # Act
        result, error = DailyLogService.update_daily_steps(user_id=778, steps=1234)

        # Assert
        assert result is None
        assert error == "Daily log not found"

    def test_TC_DL_08_update_steps_invalid_date_format_returns_error_string(self, db_session):
        """
        TC-DL-08: log_date sai format → exception được catch và trả error string.
        Input    : log_date='not-a-date'
        Expected : result=None, error != None
        """
        # Arrange: tạo user đầy đủ nhưng truyền log_date sai
        _create_full_user(779, 70.0, 175.0, db_session)

        # Act
        result, error = DailyLogService.update_daily_steps(
            user_id=779, steps=1000, log_date="not-a-date"
        )

        # Assert
        assert result is None
        assert error is not None

    def test_TC_DL_09_update_steps_exception_commit_fail_returns_error(self, db_session, monkeypatch):
        """
        TC-DL-09: Exception path — db.session.commit lỗi → rollback và trả error string.
        CheckDB  : log không bị cập nhật do rollback
        """
        # Arrange
        user_id = 780
        _create_full_user(user_id, 70.0, 175.0, db_session)
        log = DailyEnergyLog.query.filter_by(user_id=user_id, log_date=date.today()).first()
        assert log is not None

        def _boom():
            raise RuntimeError("commit failed")

        rollback_calls = {"count": 0}
        real_rollback = db.session.rollback

        def _spy_rollback():
            rollback_calls["count"] += 1
            return real_rollback()

        monkeypatch.setattr(db.session, "commit", _boom)
        monkeypatch.setattr(db.session, "rollback", _spy_rollback)

        # Act
        result, error = DailyLogService.update_daily_steps(user_id=user_id, steps=9999)

        # Assert
        assert result is None
        assert "commit failed" in error

        # CheckDB/Rollback: verify rollback được gọi khi commit lỗi
        assert rollback_calls["count"] == 1


class TestCalculateBmrAdditional:

    def test_TC_DL_10_gender_uppercase_is_handled(self):
        """
        TC-DL-10: gender='MALE' (uppercase) vẫn tính được BMR (lowercase internally).
        """
        today = date.today()
        dob = date(today.year - 20, today.month, today.day)

        bmr = calculate_bmr_from_metrics(170, 60, "MALE", dob)
        expected = int(10 * 60 + 6.25 * 170 - 5 * 20 + 5)
        assert bmr == expected

    def test_TC_DL_11_missing_gender_returns_zero(self):
        """
        TC-DL-11: gender=None → all([...]) falsy → trả 0 (thiếu dữ liệu).
        CheckDB: No; Rollback: No
        """
        today = date.today()
        dob = date(today.year - 25, today.month, today.day)
        assert calculate_bmr_from_metrics(175, 70, None, dob) == 0

    def test_TC_DL_12_missing_date_of_birth_returns_zero(self):
        """
        TC-DL-12: date_of_birth=None → trả 0.
        """
        assert calculate_bmr_from_metrics(175, 70, "male", None) == 0

    def test_TC_DL_13_non_male_uses_female_formula_branch(self):
        """
        TC-DL-13: gender không phải 'male' (ví dụ 'other') → nhánh else (công thức nữ).
        """
        today = date.today()
        dob = date(today.year - 28, today.month, today.day)
        age = today.year - dob.year - (
            (today.month, today.day) < (dob.month, dob.day)
        )
        bmr = calculate_bmr_from_metrics(165, 60, "other", dob)
        expected = int(10 * 60 + 6.25 * 165 - 5 * age - 161)
        assert bmr == expected

    def test_TC_DL_14_boundary_small_metrics_returns_int_bmr(self):
        """
        TC-DL-14: Giá trị biên nhỏ (30kg, 130cm) — vẫn trả int BMR theo công thức.
        """
        today = date.today()
        dob = date(today.year - 18, today.month, today.day)
        age = today.year - dob.year - (
            (today.month, today.day) < (dob.month, dob.day)
        )
        bmr = calculate_bmr_from_metrics(130, 30, "female", dob)
        expected = int(10 * 30 + 6.25 * 130 - 5 * age - 161)
        assert bmr == expected


class TestUpdateDailyStepsEdgeCases:

    def test_TC_DL_15_update_daily_steps_negative_steps_still_persists(self, db_session):
        """
        TC-DL-15: Service không validate steps — steps âm vẫn ghi DB (ghi nhận hành vi thực tế).
        CheckDB: total_steps âm; Rollback: fixture
        """
        user_id = 781
        _create_full_user(user_id, 70.0, 175.0, db_session)

        result, error = DailyLogService.update_daily_steps(
            user_id=user_id,
            steps=-100,
            log_date=date.today().isoformat(),
        )

        assert error is None
        assert result["total_steps"] == -100
        log = DailyEnergyLog.query.filter_by(user_id=user_id, log_date=date.today()).first()
        assert log.total_steps == -100
