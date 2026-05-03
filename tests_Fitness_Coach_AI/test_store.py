import pytest
from unittest.mock import Mock, patch
from datetime import date, timedelta
from app.memory.store import get_user_state, save_plan, is_plan_active
from app.memory.session_memory import get_session_memory, update_session_memory

class TestStoreModule:
    """Unit tests cho app/memory/store.py"""

    # ========== TEST get_user_state ==========

    @patch('app.memory.store._repo')
    def test_get_user_state_user_exists(self, mock_repo):
        """TC-STORE-01: Lấy state của user tồn tại → trả về state"""
        mock_repo.get_state.return_value = {"meal_plan": {"plan": "data"}}
        result = get_user_state("123")
        assert result == {"meal_plan": {"plan": "data"}}

    @patch('app.memory.store._repo')
    def test_get_user_state_user_not_exists(self, mock_repo):
        """TC-STORE-02: Lấy state của user không tồn tại → trả về dict rỗng"""
        mock_repo.get_state.return_value = None
        result = get_user_state("999")
        assert result == {}

    # ========== TEST save_plan ==========

    @patch('app.memory.store._repo')
    def test_save_plan_meal_plan(self, mock_repo):
        """TC-STORE-03: Lưu meal_plan với start_date và end_date"""
        mock_repo.get_state.return_value = {}
        start = date(2024, 1, 1)
        end = date(2024, 1, 7)
        save_plan("123", "meal_plan", {"breakfast": "oatmeal"}, start, end)
        
        mock_repo.save_state.assert_called_once()
        saved_state = mock_repo.save_state.call_args[0][1]
        assert saved_state["meal_plan"]["plan"] == {"breakfast": "oatmeal"}
        assert saved_state["meal_plan"]["start_date"] == "2024-01-01"

    @patch('app.memory.store._repo')
    def test_save_plan_workout_plan(self, mock_repo):
        """TC-STORE-04: Lưu workout_plan với start_date và end_date"""
        mock_repo.get_state.return_value = {}
        start = date(2024, 2, 1)
        end = date(2024, 2, 28)
        save_plan("456", "workout_plan", {"monday": "cardio"}, start, end)
        
        mock_repo.save_state.assert_called_once()
        saved_state = mock_repo.save_state.call_args[0][1]
        assert saved_state["workout_plan"]["plan"] == {"monday": "cardio"}
        assert saved_state["workout_plan"]["start_date"] == "2024-02-01"

    @patch('app.memory.store._repo')
    def test_save_plan_update_existing(self, mock_repo):
        """TC-STORE-05: Cập nhật plan đã tồn tại → ghi đè plan cũ"""
        mock_repo.get_state.return_value = {"meal_plan": {"plan": "old", "start_date": "2024-01-01", "end_date": "2024-01-07"}}
        start = date(2024, 2, 1)
        end = date(2024, 2, 7)
        save_plan("123", "meal_plan", {"breakfast": "eggs"}, start, end)
        
        saved_state = mock_repo.save_state.call_args[0][1]
        assert saved_state["meal_plan"]["plan"] == {"breakfast": "eggs"}
        assert saved_state["meal_plan"]["start_date"] == "2024-02-01"

    # ========== TEST is_plan_active ==========

    def test_is_plan_active_plan_still_valid(self):
        """TC-STORE-06: Plan còn hiệu lực (end_date >= today) → trả về True"""
        today = date.today()
        future = today + timedelta(days=7)
        state = {"meal_plan": {"end_date": future.isoformat()}}
        assert is_plan_active(state, "meal_plan") is True

    def test_is_plan_active_plan_expired(self):
        """TC-STORE-07: Plan đã hết hạn (end_date < today) → trả về False"""
        today = date.today()
        past = today - timedelta(days=7)
        state = {"meal_plan": {"end_date": past.isoformat()}}
        assert is_plan_active(state, "meal_plan") is False

    def test_is_plan_active_plan_not_exists(self):
        """TC-STORE-08: Plan không tồn tại trong state → trả về False"""
        assert is_plan_active({}, "meal_plan") is False

    def test_is_plan_active_state_is_none(self):
        """TC-STORE-09: State là None → trả về False"""
        assert is_plan_active(None, "meal_plan") is False

    def test_is_plan_active_missing_end_date(self):
        """TC-STORE-10: Plan thiếu end_date → trả về False (exception handling)"""
        state = {"meal_plan": {"plan": {}}}
        assert is_plan_active(state, "meal_plan") is False
