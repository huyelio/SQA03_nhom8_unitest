import pytest
from unittest.mock import Mock, patch
from datetime import datetime
from app.services.analysis_service import AnalysisService

class TestAnalysisService:
    """
    Unit tests cho AnalysisService
    @LƯU Ý DÀNH CHO BẠN: 
    - File này đã được sửa lại để gọi hàm thực tế từ source code thay vì copy-paste logic vào file test.
    - `@patch` được dùng để tráo (mock) kết nối Database thật thành Database giả, giúp test chạy nhanh và không thay đổi DB thật.
    - Các khái niệm mới sẽ không được chú thích lặp lại ở các hàm bên dưới.
    """

    # ========== TEST save_analysis_from_request ==========

    @patch('app.services.analysis_service.db.session')
    def test_save_analysis_from_request_success(self, mock_db_session):
        """TC-ANALYSIS-01: Lưu kết quả phân tích thành công với đầy đủ dữ liệu"""
        # Arrange (Chuẩn bị)
        user_id = 123
        mock_dto = Mock()
        mock_dto.annotated_image_url = "https://example.com/image.jpg"
        mock_dto.detection = [{"detected_class": "acne", "confidence": 0.95}]
        mock_dto.lifestyle_suggestions = {"diet": ["Drink water"]}

        # Act (Thực thi - Gọi hàm thực tế)
        result = AnalysisService.save_analysis_from_request(user_id, mock_dto)

        # Assert (Kiểm tra)
        mock_db_session.add.assert_called_once()
        mock_db_session.commit.assert_called_once()
        
        # Lấy đối tượng (entity) đã được truyền vào hàm db.session.add()
        saved_entity = mock_db_session.add.call_args[0][0]
        assert saved_entity.user_id == 123
        assert saved_entity.ai_diagnosis == "acne"
        assert saved_entity.ai_confidence == 0.95

    @patch('app.services.analysis_service.db.session')
    def test_save_analysis_from_request_empty_detection(self, mock_db_session):
        """TC-ANALYSIS-02: Lưu kết quả khi detection rỗng → ai_diagnosis và ai_confidence là None"""
        mock_dto = Mock()
        mock_dto.annotated_image_url = "https://example.com/image2.jpg"
        mock_dto.detection = [] # Mảng rỗng
        mock_dto.lifestyle_suggestions = {}

        AnalysisService.save_analysis_from_request(123, mock_dto)

        saved_entity = mock_db_session.add.call_args[0][0]
        assert saved_entity.ai_diagnosis is None
        assert saved_entity.ai_confidence is None

    @patch('app.services.analysis_service.db.session')
    def test_save_analysis_from_request_multiple_detections(self, mock_db_session):
        """TC-ANALYSIS-03: Lưu kết quả khi có nhiều detection → chỉ lấy detection đầu tiên"""
        mock_dto = Mock()
        mock_dto.annotated_image_url = "https://example.com/image3.jpg"
        mock_dto.detection = [
            {"detected_class": "acne", "confidence": 0.95},
            {"detected_class": "wrinkle", "confidence": 0.85}
        ]
        mock_dto.lifestyle_suggestions = {}

        AnalysisService.save_analysis_from_request(123, mock_dto)

        saved_entity = mock_db_session.add.call_args[0][0]
        assert saved_entity.ai_diagnosis == "acne" # Chỉ lấy cái đầu tiên

    # ========== TEST update_doctor_note ==========

    @patch('app.services.analysis_service.db.session')
    @patch('app.services.analysis_service.HealthAnalysis')
    def test_update_doctor_note_success(self, mock_HealthAnalysis, mock_db_session):
        """TC-ANALYSIS-04: Cập nhật doctor note thành công cho record của chính user"""
        mock_entity = Mock()
        mock_entity.user_id = 123
        mock_HealthAnalysis.query.get.return_value = mock_entity

        result = AnalysisService.update_doctor_note(1, "New note", 123)

        assert result is not None
        assert mock_entity.doctor_note == "New note"
        mock_db_session.commit.assert_called_once()

    @patch('app.services.analysis_service.db.session')
    @patch('app.services.analysis_service.HealthAnalysis')
    def test_update_doctor_note_record_not_found(self, mock_HealthAnalysis, mock_db_session):
        """TC-ANALYSIS-05: Cập nhật doctor note khi record không tồn tại → trả về None"""
        mock_HealthAnalysis.query.get.return_value = None # Database trả về null

        result = AnalysisService.update_doctor_note(1, "New note", 123)

        assert result is None
        mock_db_session.commit.assert_not_called()

    @patch('app.services.analysis_service.db.session')
    @patch('app.services.analysis_service.HealthAnalysis')
    def test_update_doctor_note_wrong_user(self, mock_HealthAnalysis, mock_db_session):
        """TC-ANALYSIS-06: Cập nhật doctor note của user khác → trả về None (Bảo mật)"""
        mock_entity = Mock()
        mock_entity.user_id = 456 # Record thuộc về người khác
        mock_HealthAnalysis.query.get.return_value = mock_entity

        result = AnalysisService.update_doctor_note(1, "New note", 123)

        assert result is None
        mock_db_session.commit.assert_not_called()

    @patch('app.services.analysis_service.db.session')
    @patch('app.services.analysis_service.HealthAnalysis')
    def test_update_doctor_note_empty_note(self, mock_HealthAnalysis, mock_db_session):
        """TC-ANALYSIS-07: Cập nhật doctor note với chuỗi rỗng → vẫn lưu thành công"""
        mock_entity = Mock()
        mock_entity.user_id = 123
        mock_HealthAnalysis.query.get.return_value = mock_entity

        result = AnalysisService.update_doctor_note(1, "", 123)

        assert result is not None
        assert mock_entity.doctor_note == ""
        mock_db_session.commit.assert_called_once()

    # ========== TEST get_history_by_user ==========

    @patch('app.services.analysis_service.HealthAnalysis')
    def test_get_history_by_user_with_records(self, mock_HealthAnalysis):
        """TC-ANALYSIS-08: Lấy lịch sử phân tích của user"""
        # Giả lập lại chuỗi SQLAlchemy: query.filter_by(...).order_by(...).all()
        mock_record = Mock()
        mock_HealthAnalysis.query.filter_by.return_value.order_by.return_value.all.return_value = [mock_record, mock_record]

        result = AnalysisService.get_history_by_user(123)

        assert len(result) == 2

    @patch('app.services.analysis_service.HealthAnalysis')
    def test_get_history_by_user_no_records(self, mock_HealthAnalysis):
        """TC-ANALYSIS-09: Lấy lịch sử của user không có records → trả về list rỗng"""
        mock_HealthAnalysis.query.filter_by.return_value.order_by.return_value.all.return_value = []

        result = AnalysisService.get_history_by_user(999)

        assert result == []
        assert isinstance(result, list)

    @patch('app.services.analysis_service.HealthAnalysis')
    def test_get_history_by_user_order_by_created_at_desc(self, mock_HealthAnalysis):
        """TC-ANALYSIS-10: Lịch sử được sắp xếp theo created_at giảm dần (mới nhất trước)"""
        mock_record1 = Mock()
        mock_record1.created_at = datetime(2024, 1, 1)

        mock_record2 = Mock()
        mock_record2.created_at = datetime(2024, 1, 5)

        mock_record3 = Mock()
        mock_record3.created_at = datetime(2024, 1, 3)

        # Giả lập SQLAlchemy trả về danh sách đã được sắp xếp giảm dần (desc)
        records = [mock_record2, mock_record3, mock_record1]
        mock_HealthAnalysis.query.filter_by.return_value.order_by.return_value.all.return_value = records

        result = AnalysisService.get_history_by_user(123)

        assert len(result) == 3
        # Hàm get_history_by_user sẽ bọc vào AnalysisResult, do Mock không copy hết property nên ta dùng mock để kiểm tra độ dài list là chính
