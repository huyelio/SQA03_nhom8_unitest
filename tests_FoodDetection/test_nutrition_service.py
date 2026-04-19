"""
test_nutrition_service.py – Unit tests cho NutritionService.

Yêu cầu:
  - Không gọi external nutrition DB thật
  - Phải mock dependency calculate_total_nutrition (app.utils)

Test cases:
  TC-NUT-01: analyze trả đúng dữ liệu từ calculate_total_nutrition (Positive)
  TC-NUT-02: analyze với detections=[] → trả totals = 0 (Positive)
  TC-NUT-03: analyze với detections=None → exception (Exception)
  TC-NUT-04~07: timeout / ConnectionError / empty response / ValueError từ dependency đã mock
"""

import pytest

from app.services.nutrition_service import NutritionService


def test_TC_NUT_01_analyze_calls_calculate_total_nutrition_and_returns_value(mocker):
    """
    TC-NUT-01: analyze gọi calculate_total_nutrition và trả đúng kết quả.
    Arrange  : mock app.services.nutrition_service.calculate_total_nutrition
    Act      : NutritionService.analyze(detections)
    Assert   : trả đúng dict; mock được gọi 1 lần
    CheckDB  : No
    Rollback : No
    """
    # Arrange
    detections = [{"detected_class": "banana"}]
    expected = {"items_count": 1, "total_nutrition": {"Calories": 100}}

    mocked = mocker.patch(
        "app.services.nutrition_service.calculate_total_nutrition",
        return_value=expected,
    )

    # Act
    result = NutritionService.analyze(detections)

    # Assert
    assert result == expected
    mocked.assert_called_once_with(detections)


def test_TC_NUT_02_analyze_empty_list_returns_zero_totals(mocker):
    """
    TC-NUT-02: detections=[] → totals 0.
    Ở đây vẫn mock calculate_total_nutrition để đảm bảo không phụ thuộc FOOD_NUTRITION_DB thật.
    """
    # Arrange
    detections = []
    expected = {"items_count": 0, "total_nutrition": {"Calories": 0, "Fat": 0, "Carbs": 0, "Protein": 0}}
    mocker.patch(
        "app.services.nutrition_service.calculate_total_nutrition",
        return_value=expected,
    )

    # Act
    result = NutritionService.analyze(detections)

    # Assert
    assert result["items_count"] == 0
    assert result["total_nutrition"]["Calories"] == 0


def test_TC_NUT_03_analyze_none_raises_exception(mocker):
    """
    TC-NUT-03: detections=None → calculate_total_nutrition ném TypeError → propagate (Exception case).
    """
    # Arrange
    mocker.patch(
        "app.services.nutrition_service.calculate_total_nutrition",
        side_effect=TypeError("detections must be iterable"),
    )

    # Act + Assert
    with pytest.raises(TypeError):
        NutritionService.analyze(None)


def test_TC_NUT_04_analyze_external_dependency_timeout_propagates(mocker):
    """
    TC-NUT-04: calculate_total_nutrition giả lập timeout/OSError → exception lan truyền.
    CheckDB: No; Rollback: No
    """
    mocker.patch(
        "app.services.nutrition_service.calculate_total_nutrition",
        side_effect=TimeoutError("upstream timeout"),
    )
    with pytest.raises(TimeoutError, match="upstream timeout"):
        NutritionService.analyze([{"detected_class": "rice"}])


def test_TC_NUT_05_analyze_external_raises_connection_error(mocker):
    """
    TC-NUT-05: Dependency ném ConnectionError (mô phỏng lỗi mạng/DB ngoài).
    """
    mocker.patch(
        "app.services.nutrition_service.calculate_total_nutrition",
        side_effect=ConnectionError("nutrition db unreachable"),
    )
    with pytest.raises(ConnectionError):
        NutritionService.analyze([{"detected_class": "x"}])


def test_TC_NUT_06_analyze_returns_empty_mapping_response(mocker):
    """
    TC-NUT-06: Mock trả dict rỗng / không có key chuẩn — analyze vẫn trả đúng object mock (response rỗng).
    """
    expected = {}
    mocker.patch(
        "app.services.nutrition_service.calculate_total_nutrition",
        return_value=expected,
    )
    result = NutritionService.analyze([])
    assert result == {}


def test_TC_NUT_07_analyze_propagates_value_error_from_dependency(mocker):
    """
    TC-NUT-07: calculate_total_nutrition ném ValueError (dữ liệu không hợp lệ sau mapping).
    """
    mocker.patch(
        "app.services.nutrition_service.calculate_total_nutrition",
        side_effect=ValueError("invalid nutrition payload"),
    )
    with pytest.raises(ValueError, match="invalid nutrition payload"):
        NutritionService.analyze([{"detected_class": "bad"}])

