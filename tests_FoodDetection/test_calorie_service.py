"""
test_calorie_service.py – Unit tests cho CalorieService.

Test cases:
  TC-CAL-01: add_food_records thành công, daily_log chưa tồn tại → 201
  TC-CAL-02: add_food_records payload foods rỗng → 400
  TC-CAL-03: update_food_record bởi user khác → 403
  TC-CAL-04: delete_food_record thành công → cập nhật total_calorie_in
  TC-CAL-05: get_food_records ngày không có log → 200 với danh sách rỗng
  TC-CAL-06: add_food_records với nhiều food items, daily_log đã tồn tại → cộng đúng calo
  TC-CAL-18~21: commit fail, log_date None, KeyError thiếu food_name, boundary quantity/calorie
"""

import json
import pytest
from datetime import date

from app.extensions import db
from app.models.user_profile import UserProfile
from app.models.daily_energy_log import DailyEnergyLog
from app.models.food_record import FoodRecord
from app.enums.app_enum import ActivityLevelEnum
from app.services.calorie_service import CalorieService


# ===========================================================================
# Helpers tạo test data
# ===========================================================================

def _create_user_profile(user_id: int, session) -> UserProfile:
    """Tạo UserProfile tối giản cho test."""
    profile = UserProfile(
        user_id=user_id,
        gender="male",
        activity_level=ActivityLevelEnum.sedentary,
        aim_weight=70.0,
    )
    session.add(profile)
    session.flush()
    return profile


def _create_daily_log(user_id: int, log_date: date, total_calorie_in: int, session) -> DailyEnergyLog:
    """Tạo DailyEnergyLog cho test."""
    log = DailyEnergyLog(
        user_id=user_id,
        log_date=log_date,
        total_calorie_in=total_calorie_in,
        steps_calorie_out=0,
    )
    session.add(log)
    session.flush()
    return log


def _create_food_record(daily_log_id: int, food_name: str, calorie: int, session) -> FoodRecord:
    """Tạo FoodRecord cho test."""
    record = FoodRecord(
        daily_log_id=daily_log_id,
        food_name=food_name,
        calorie=calorie,
        quantity=1.0,
        input_method="manual",
    )
    session.add(record)
    session.flush()
    return record


# ===========================================================================
# Tests
# ===========================================================================

class TestAddFoodRecords:

    def test_TC_CAL_01_add_food_success_creates_daily_log(self, db_session):
        """
        TC-CAL-01: Thêm 1 food thành công khi daily_log chưa tồn tại.
        Input    : user_id=101, payload với 1 food (calorie=300)
        Expected : HTTP 201, items_added=1
        CheckDB  : daily_energy_logs và food_records có row mới
        Rollback : db_session fixture tự động rollback
        """
        user_id = 101
        test_date = "2026-01-15"

        payload = {
            "log_date": test_date,
            "foods": [
                {"food_name": "Cơm trắng", "calorie": 300, "quantity": 1, "input_method": "manual"}
            ]
        }

        # Act
        response, status = CalorieService.add_food_records(user_id, payload)

        # Assert HTTP status
        assert status == 201

        data = json.loads(response.data)
        assert data["items_added"] == 1
        assert data["total_calorie_added"] == 300

        # CheckDB: daily_energy_log mới tồn tại trong DB
        log = DailyEnergyLog.query.filter_by(
            user_id=user_id, log_date=date.fromisoformat(test_date)
        ).first()
        assert log is not None, "DailyEnergyLog phải được tạo mới trong DB"
        assert log.total_calorie_in == 300

        # CheckDB: food_record mới tồn tại
        records = FoodRecord.query.filter_by(daily_log_id=log.id).all()
        assert len(records) == 1
        assert records[0].food_name == "Cơm trắng"

    def test_TC_CAL_02_add_food_empty_foods_returns_400(self, db_session):
        """
        TC-CAL-02: Payload foods rỗng → HTTP 400.
        Input    : user_id=102, payload không có foods
        Expected : HTTP 400, error "No food provided"
        CheckDB  : Không có row mới được tạo trong DB
        """
        user_id = 102
        payload = {"log_date": "2026-01-15", "foods": []}

        # Act
        response, status = CalorieService.add_food_records(user_id, payload)

        # Assert
        assert status == 400
        data = json.loads(response.data)
        assert "No food provided" in data["error"]

        # CheckDB: không tạo log mới
        log = DailyEnergyLog.query.filter_by(user_id=user_id).first()
        assert log is None, "Không được tạo DailyEnergyLog khi foods rỗng"

    def test_TC_CAL_06_add_multiple_foods_to_existing_log(self, db_session):
        """
        TC-CAL-06: Thêm nhiều food vào daily_log đã tồn tại → cộng đúng tổng calo.
        Input    : user_id=106, daily_log đã có total_calorie_in=200, thêm 2 food (100 + 150)
        Expected : items_added=2, total_calorie_in_by_day = 200 + 100 + 150 = 450
        CheckDB  : total_calorie_in trong DB đúng 450
        Rollback : db_session tự động rollback
        """
        user_id = 106
        test_date = date(2026, 1, 16)

        # Chuẩn bị: tạo daily_log đã có sẵn 200 calo
        existing_log = _create_daily_log(user_id, test_date, 200, db_session)

        payload = {
            "log_date": test_date.isoformat(),
            "foods": [
                {"food_name": "Trứng chiên", "calorie": 100, "quantity": 1, "input_method": "manual"},
                {"food_name": "Chuối",       "calorie": 150, "quantity": 1, "input_method": "manual"},
            ]
        }

        # Act
        response, status = CalorieService.add_food_records(user_id, payload)

        # Assert
        assert status == 201
        data = json.loads(response.data)
        assert data["items_added"] == 2
        assert data["total_calorie_in_by_day"] == 450  # 200 + 100 + 150

        # CheckDB: DB phản ánh đúng tổng calo
        db_session.refresh(existing_log)
        assert existing_log.total_calorie_in == 450


class TestUpdateFoodRecord:

    def test_TC_CAL_03_update_record_wrong_user_returns_403(self, db_session):
        """
        TC-CAL-03: User khác cố cập nhật food record → HTTP 403 Unauthorized.
        Input    : record thuộc user_id=201, nhưng caller là user_id=999
        Expected : HTTP 403
        CheckDB  : Không có thay đổi trong DB
        """
        # Chuẩn bị: tạo log và record thuộc user 201
        log = _create_daily_log(201, date(2026, 1, 20), 0, db_session)
        record = _create_food_record(log.id, "Phở bò", 500, db_session)

        # Payload chứa record_id nhưng caller là user 999
        payload = {
            "id": record.id,
            "food_name": "Bún bò",
            "calorie": 600,
        }

        # Act: user 999 cố sửa record của user 201
        result, status = CalorieService.update_food_record(999, payload)

        # Assert
        assert status == 403
        assert "Unauthorized" in result["error"]

        # CheckDB: food_name không thay đổi
        db_session.refresh(record)
        assert record.food_name == "Phở bò"

    def test_TC_CAL_07_update_record_missing_id_returns_400(self, db_session):
        """
        TC-CAL-07: Thiếu id trong payload → HTTP 400.
        Input    : payload không có "id"
        Expected : HTTP 400, error "Food record id is required"
        CheckDB  : Không có thay đổi DB
        """
        # Arrange
        payload = {"food_name": "Bún bò", "calorie": 600}

        # Act
        result, status = CalorieService.update_food_record(1, payload)

        # Assert
        assert status == 400
        assert "Food record id is required" in result["error"]

    def test_TC_CAL_08_update_record_not_found_returns_404(self, db_session):
        """
        TC-CAL-08: Update record không tồn tại → 404.
        Input    : id=999999
        Expected : 404, error "Food record not found"
        CheckDB  : Không có thay đổi DB
        """
        # Arrange
        payload = {"id": 999999, "food_name": "Bún bò", "calorie": 600}

        # Act
        result, status = CalorieService.update_food_record(1, payload)

        # Assert
        assert status == 404
        assert "Food record not found" in result["error"]

    def test_TC_CAL_09_update_record_success_recalculates_total(self, db_session):
        """
        TC-CAL-09: Update record (đúng user) → cập nhật record và tính lại total_calorie_in.
        Arrange  : log có 2 records: 200 + 300
        Act      : update record1 calorie -> 500
        Expected : daily_log.total_calorie_in = 500 + 300 = 800
        CheckDB  : verify record + daily_log trong DB
        Rollback : db_session fixture rollback
        """
        # Arrange
        user_id = 401
        log = _create_daily_log(user_id, date(2026, 2, 1), 0, db_session)
        r1 = _create_food_record(log.id, "A", 200, db_session)
        _create_food_record(log.id, "B", 300, db_session)
        log.total_calorie_in = 500
        db_session.flush()

        payload = {"id": r1.id, "food_name": "A+", "calorie": 500}

        # Act
        result, status = CalorieService.update_food_record(user_id, payload)

        # Assert
        assert status == 200
        assert result["id"] == r1.id
        assert result["food_name"] == "A+"
        assert result["calorie"] == 500

        # CheckDB
        db_session.refresh(r1)
        assert r1.food_name == "A+"
        assert r1.calorie == 500
        db_session.refresh(log)
        assert log.total_calorie_in == 800

    def test_TC_CAL_10_update_record_exception_commit_fail_returns_500(self, db_session, monkeypatch):
        """
        TC-CAL-10: Exception path — db.session.commit lỗi → trả 500 và rollback.
        Input    : record hợp lệ
        Expected : HTTP 500, error chứa message
        CheckDB  : record không bị thay đổi (rollback)
        """
        # Arrange
        user_id = 402
        log = _create_daily_log(user_id, date(2026, 2, 2), 0, db_session)
        record = _create_food_record(log.id, "Before", 100, db_session)

        def _boom():
            raise RuntimeError("db commit failed")

        rollback_calls = {"count": 0}
        real_rollback = db.session.rollback

        def _spy_rollback():
            rollback_calls["count"] += 1
            return real_rollback()

        monkeypatch.setattr(db.session, "commit", _boom)
        monkeypatch.setattr(db.session, "rollback", _spy_rollback)

        payload = {"id": record.id, "food_name": "After", "calorie": 999}

        # Act
        result, status = CalorieService.update_food_record(user_id, payload)

        # Assert
        assert status == 500
        assert "db commit failed" in result["error"]

        # CheckDB/Rollback: verify rollback được gọi khi commit lỗi
        assert rollback_calls["count"] == 1


class TestDeleteFoodRecord:

    def test_TC_CAL_04_delete_record_updates_total_calorie(self, db_session):
        """
        TC-CAL-04: Xóa food record thành công → total_calorie_in được cập nhật lại.
        Input    : user_id=301, log có 2 food records (200 + 300 calo), xóa record đầu
        Expected : HTTP 200, total_calorie_in = 300 (chỉ còn 1 record)
        CheckDB  : food record bị xóa; total_calorie_in trong DB đúng 300
        Rollback : db_session tự động rollback
        """
        user_id = 301
        log = _create_daily_log(user_id, date(2026, 1, 25), 0, db_session)

        record1 = _create_food_record(log.id, "Bữa sáng", 200, db_session)
        record2 = _create_food_record(log.id, "Bữa trưa", 300, db_session)

        # Cập nhật tổng calo ban đầu
        log.total_calorie_in = 500
        db_session.flush()

        # Act: xóa record1
        result, status = CalorieService.delete_food_record(user_id, record1.id)

        # Assert HTTP response
        assert status == 200
        assert result["message"] == "Food record deleted successfully"
        assert result["total_calorie_in"] == 300  # chỉ còn record2

        # CheckDB: record1 đã bị xóa khỏi DB
        deleted = FoodRecord.query.get(record1.id)
        assert deleted is None, "record1 phải bị xóa khỏi DB"

        # CheckDB: total_calorie_in trong DB đúng 300
        db_session.refresh(log)
        assert log.total_calorie_in == 300

    def test_TC_CAL_11_delete_record_not_found_returns_404(self, db_session):
        """
        TC-CAL-11: delete_food_record record không tồn tại → 404.
        Expected : 404, error \"Food record not found\"
        """
        # Act
        result, status = CalorieService.delete_food_record(1, 999999)

        # Assert
        assert status == 404
        assert "Food record not found" in result["error"]

    def test_TC_CAL_12_delete_record_unauthorized_returns_403(self, db_session):
        """
        TC-CAL-12: delete_food_record — record thuộc user khác → 403 Unauthorized.
        Arrange  : log thuộc user 700, record thuộc log đó
        Act      : caller user 701 xóa
        Expected : 403, không xóa record
        CheckDB  : record vẫn tồn tại
        """
        # Arrange
        owner_id = 700
        caller_id = 701
        log = _create_daily_log(owner_id, date(2026, 2, 3), 0, db_session)
        record = _create_food_record(log.id, "OwnerFood", 123, db_session)

        # Act
        result, status = CalorieService.delete_food_record(caller_id, record.id)

        # Assert
        assert status == 403
        assert "Unauthorized" in result["error"]

        # CheckDB: record vẫn tồn tại
        still = FoodRecord.query.filter_by(id=record.id).first()
        assert still is not None

    def test_TC_CAL_13_delete_record_exception_commit_fail_returns_500(self, db_session, monkeypatch):
        """
        TC-CAL-13: Exception path — commit lỗi → 500 + rollback.
        Arrange  : record thuộc đúng user
        Expected : 500, record vẫn còn trong DB
        """
        # Arrange
        user_id = 800
        log = _create_daily_log(user_id, date(2026, 2, 4), 0, db_session)
        record = _create_food_record(log.id, "ToDelete", 200, db_session)

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
        result, status = CalorieService.delete_food_record(user_id, record.id)

        # Assert
        assert status == 500
        assert "commit failed" in result["error"]

        # CheckDB/Rollback: verify rollback được gọi khi commit lỗi
        assert rollback_calls["count"] == 1


class TestGetFoodRecordsAdditional:

    def test_TC_CAL_14_get_food_records_existing_log_returns_foods(self, db_session):
        """
        TC-CAL-14: get_food_records — có daily_log và foods → trả đúng foods list.
        CheckDB  : query DB trả đúng số lượng food records
        """
        # Arrange
        user_id = 900
        log_date = date(2026, 2, 5)
        log = _create_daily_log(user_id, log_date, 0, db_session)
        _create_food_record(log.id, "X", 111, db_session)
        _create_food_record(log.id, "Y", 222, db_session)
        log.total_calorie_in = 333
        db_session.flush()

        # Act
        resp, status = CalorieService.get_food_records(user_id, log_date.isoformat())

        # Assert
        assert status == 200
        data = json.loads(resp.data)
        assert data["summary"]["total_calorie_in"] == 333
        assert len(data["foods"]) == 2

    def test_TC_CAL_15_get_food_records_invalid_date_raises_value_error(self, db_session):
        """
        TC-CAL-15: get_food_records — log_date sai format → ValueError (exception).
        Input    : log_date='not-a-date'
        Expected : ValueError được raise
        """
        with pytest.raises(ValueError):
            CalorieService.get_food_records(1, "not-a-date")


class TestAddFoodRecordsAdditional:

    def test_TC_CAL_16_add_food_records_invalid_date_raises_value_error(self, db_session):
        """
        TC-CAL-16: add_food_records — log_date sai format → ValueError (exception).
        Input    : payload.log_date='invalid-date'
        Expected : ValueError được raise (service không catch)
        """
        payload = {
            "log_date": "invalid-date",
            "foods": [{"food_name": "A", "calorie": 100, "quantity": 1}],
        }
        with pytest.raises(ValueError):
            CalorieService.add_food_records(1, payload)

    def test_TC_CAL_17_add_food_records_calorie_not_int_raises_exception(self, db_session):
        """
        TC-CAL-17: add_food_records — calorie không ép int được → Exception.
        Input    : calorie='abc'
        Expected : ValueError / Exception
        """
        payload = {
            "log_date": "2026-01-15",
            "foods": [{"food_name": "A", "calorie": "abc", "quantity": 1}],
        }
        with pytest.raises(Exception):
            CalorieService.add_food_records(1, payload)

class TestAddFoodRecordsCommitAndEdgeCases:

    def test_TC_CAL_18_add_food_records_commit_failure_propagates(self, db_session, monkeypatch):
        """
        TC-CAL-18: add_food_records — db.session.commit lỗi → exception lan truyền (service không bắt try/except).
        CheckDB: commit không hoàn tất; Rollback: fixture db_session vẫn rollback toàn test.
        """
        def _boom():
            raise RuntimeError("persist failed")

        monkeypatch.setattr(db.session, "commit", _boom)

        payload = {
            "log_date": "2026-03-01",
            "foods": [{"food_name": "X", "calorie": 100, "quantity": 1}],
        }

        with pytest.raises(RuntimeError, match="persist failed"):
            CalorieService.add_food_records(501, payload)

    def test_TC_CAL_20_add_food_records_missing_food_name_raises_keyerror(self, db_session):
        """
        TC-CAL-20: Thiếu key food_name trong phần tử foods → KeyError (không validate payload).
        CheckDB: No (exception trước commit); Rollback: N/A
        """
        payload = {
            "log_date": "2026-03-02",
            "foods": [{"calorie": 100, "quantity": 1}],
        }
        with pytest.raises(KeyError):
            CalorieService.add_food_records(502, payload)

    def test_TC_CAL_21_add_food_records_unusual_quantity_and_calorie(self, db_session):
        """
        TC-CAL-21: Nhiều món + quantity lớn + calorie biên — tổng calo = sum(calorie * quantity).
        CheckDB: total_calorie_in đúng; Rollback: fixture
        """
        user_id = 503
        payload = {
            "log_date": "2026-03-03",
            "foods": [
                {"food_name": "A", "calorie": 1, "quantity": 1000, "input_method": "manual"},
                {"food_name": "B", "calorie": 9999, "quantity": 1, "input_method": "manual"},
            ],
        }
        response, status = CalorieService.add_food_records(user_id, payload)
        assert status == 201
        data = json.loads(response.data)
        assert data["total_calorie_added"] == 1000 + 9999
        log = DailyEnergyLog.query.filter_by(user_id=user_id).first()
        assert log.total_calorie_in == 10999


class TestGetFoodRecords:

    def test_TC_CAL_19_get_food_records_log_date_none_uses_today(self, db_session):
        """
        TC-CAL-19: log_date=None → dùng date.today(); trả đúng foods của log hôm nay.
        CheckDB: Yes; Rollback: fixture
        """
        user_id = 504
        log = _create_daily_log(user_id, date.today(), 0, db_session)
        _create_food_record(log.id, "TodayFood", 50, db_session)
        log.total_calorie_in = 50
        db_session.flush()

        resp, status = CalorieService.get_food_records(user_id, None)
        assert status == 200
        data = json.loads(resp.data)
        assert len(data["foods"]) == 1
        assert data["foods"][0]["food_name"] == "TodayFood"

    def test_TC_CAL_05_get_records_no_log_returns_empty(self, db_session):
        """
        TC-CAL-05: Lấy food records cho ngày không có log → trả danh sách rỗng.
        Input    : user_id=401, log_date không có trong DB
        Expected : HTTP 200, foods=[], total_calorie_in=0
        CheckDB  : Chỉ đọc DB, không thay đổi
        """
        user_id = 401
        log_date = "2026-06-01"  # Ngày chắc chắn không có data test

        # Act
        response, status = CalorieService.get_food_records(user_id, log_date)

        # Assert
        assert status == 200
        data = json.loads(response.data)
        assert data["foods"] == []
        assert data["summary"]["total_calorie_in"] == 0
