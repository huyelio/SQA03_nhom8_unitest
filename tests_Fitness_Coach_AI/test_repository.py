import pytest
from unittest.mock import Mock, patch
from app.memory.repository import UserStateRepositoryImpl

class TestUserStateRepositoryImpl:
    """
    Unit tests cho UserStateRepositoryImpl
    - Đã sửa để khởi tạo repository thật và gọi hàm thật (`get_state`, `save_state`).
    - Dùng `@patch` giả lập `db.session` (Database) để không đụng tới dữ liệu thực tế.
    """

    def setup_method(self):
        self.repo = UserStateRepositoryImpl()

    # ========== TEST get_state ==========

    @patch('app.memory.repository.db.session')
    def test_get_state_user_exists_with_plans(self, mock_db_session):
        """TC-REPO-01: Lấy state của user tồn tại có meal_plan và workout_plan"""
        mock_plan = Mock()
        mock_plan.meal_plan = {"breakfast": "oatmeal"}
        mock_plan.workout_plan = {"monday": "cardio"}
        mock_db_session.query.return_value.filter.return_value.first.return_value = mock_plan

        result = self.repo.get_state("123")

        assert result == {
            "meal_plan": {"breakfast": "oatmeal"},
            "workout_plan": {"monday": "cardio"}
        }

    @patch('app.memory.repository.db.session')
    def test_get_state_user_not_exists(self, mock_db_session):
        """TC-REPO-02: Lấy state của user không tồn tại → trả về dict rỗng"""
        mock_db_session.query.return_value.filter.return_value.first.return_value = None

        result = self.repo.get_state("123")
        assert result == {}

    @patch('app.memory.repository.db.session')
    def test_get_state_user_with_null_plans(self, mock_db_session):
        """TC-REPO-03: Lấy state của user có meal_plan và workout_plan là None"""
        mock_plan = Mock()
        mock_plan.meal_plan = None
        mock_plan.workout_plan = None
        mock_db_session.query.return_value.filter.return_value.first.return_value = mock_plan

        result = self.repo.get_state("123")
        
        assert result == {
            "meal_plan": None,
            "workout_plan": None
        }

    # ========== TEST save_state ==========

    @patch('app.memory.repository.UserPlan')
    @patch('app.memory.repository.db.session')
    def test_save_state_insert_new_user(self, mock_db_session, mock_UserPlan_class):
        """TC-REPO-04: Lưu state cho user mới (INSERT) → db.session.add được gọi"""
        mock_db_session.query.return_value.filter.return_value.first.return_value = None
        
        state = {
            "meal_plan": {"breakfast": "eggs"},
            "workout_plan": {"wednesday": "yoga"}
        }
        self.repo.save_state("789", state)
        
        mock_db_session.add.assert_called_once()
        mock_db_session.commit.assert_called_once()
        mock_UserPlan_class.assert_called_once_with(
            user_id=789,
            meal_plan={"breakfast": "eggs"},
            workout_plan={"wednesday": "yoga"}
        )

    @patch('app.memory.repository.db.session')
    def test_save_state_update_existing_user_both_fields(self, mock_db_session):
        """TC-REPO-05: Cập nhật state cho user đã tồn tại (UPDATE cả 2 field)"""
        mock_plan = Mock()
        mock_db_session.query.return_value.filter.return_value.first.return_value = mock_plan
        
        state = {
            "meal_plan": {"dinner": "chicken"},
            "workout_plan": {"friday": "swimming"}
        }
        self.repo.save_state("123", state)
        
        mock_db_session.add.assert_not_called()
        mock_db_session.commit.assert_called_once()
        assert mock_plan.meal_plan == {"dinner": "chicken"}
        assert mock_plan.workout_plan == {"friday": "swimming"}

    @patch('app.memory.repository.db.session')
    def test_save_state_update_only_meal_plan(self, mock_db_session):
        """TC-REPO-06: Cập nhật chỉ meal_plan, workout_plan giữ nguyên"""
        mock_plan = Mock()
        mock_plan.meal_plan = {"breakfast": "old"}
        mock_plan.workout_plan = {"monday": "cardio"}
        mock_db_session.query.return_value.filter.return_value.first.return_value = mock_plan
        
        state = {"meal_plan": {"lunch": "pasta"}}
        self.repo.save_state("123", state)
        
        assert mock_plan.meal_plan == {"lunch": "pasta"}
        assert mock_plan.workout_plan == {"monday": "cardio"}

    @patch('app.memory.repository.db.session')
    def test_save_state_update_only_workout_plan(self, mock_db_session):
        """TC-REPO-07: Cập nhật chỉ workout_plan, meal_plan giữ nguyên"""
        mock_plan = Mock()
        mock_plan.meal_plan = {"breakfast": "cereal"}
        mock_plan.workout_plan = {"monday": "old"}
        mock_db_session.query.return_value.filter.return_value.first.return_value = mock_plan
        
        state = {"workout_plan": {"saturday": "rest"}}
        self.repo.save_state("123", state)
        
        assert mock_plan.meal_plan == {"breakfast": "cereal"}
        assert mock_plan.workout_plan == {"saturday": "rest"}

    @patch('app.memory.repository.db.session')
    def test_save_state_empty_state_dict(self, mock_db_session):
        """TC-REPO-08: Lưu state với dict rỗng → không cập nhật gì"""
        mock_plan = Mock()
        mock_plan.meal_plan = {"breakfast": "original"}
        mock_plan.workout_plan = {"monday": "original"}
        mock_db_session.query.return_value.filter.return_value.first.return_value = mock_plan
        
        state = {}
        self.repo.save_state("123", state)
        
        assert mock_plan.meal_plan == {"breakfast": "original"}
        assert mock_plan.workout_plan == {"monday": "original"}
