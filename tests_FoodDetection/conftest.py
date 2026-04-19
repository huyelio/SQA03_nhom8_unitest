"""
conftest.py – Cấu hình pytest cho Food_Detection_Microservices.

Chiến lược:
  - Dùng SQLite in-memory thay vì MySQL thật để test nhanh, độc lập.
  - Mock các thư viện nặng (onnxruntime, cv2, cloudinary) qua sys.modules
    để chạy test LOCAL mà không cần cài đầy đủ AI/ML dependencies.
  - Mỗi test dùng SAVEPOINT (begin_nested) → rollback sau khi chạy xong.
  - Đáp ứng yêu cầu CheckDB (truy vấn DB thật) và Rollback.
"""

import sys
import types
import pytest
from flask import Flask
from unittest.mock import patch, MagicMock

# ── Mock heavy / AI-only libraries BEFORE any app import ─────────────────────
# Những thư viện này không cần thiết cho logic business (calorie, daily_log)
# nhưng được import ở module level trong routes và services_AI.
_HEAVY_MOCKS = [
    "onnxruntime", "cv2",
    "cloudinary", "cloudinary.uploader", "cloudinary.utils", "cloudinary.api",
    "PIL", "PIL.Image",   # PIL mock OK vì routes không được gọi trong test
    # requests có thể không được cài ở môi trường local → mock để tránh import error
    "requests",
    # numpy KHÔNG mock → numpy thật cần cho pytest.approx
    "flask_migrate",
]
for _mod in _HEAVY_MOCKS:
    if _mod not in sys.modules:
        sys.modules[_mod] = MagicMock()

# dotenv mock nếu chưa cài
try:
    from dotenv import load_dotenv  # noqa: F401
except ImportError:
    _dotenv = types.ModuleType("dotenv")
    _dotenv.load_dotenv = lambda *a, **kw: None
    sys.modules["dotenv"] = _dotenv

# ── Sau khi mock xong mới import app ─────────────────────────────────────────
from app.extensions import db

# ── SQLite compat: BigInteger → INTEGER (cho phép auto-increment) ─────────────
# SQLite chỉ auto-increment khi kiểu là INTEGER (không phải BIGINT).
from sqlalchemy import BigInteger
from sqlalchemy.ext.compiler import compiles

@compiles(BigInteger, "sqlite")
def _bigint_to_int_sqlite(type_, compiler, **kw):
    return "INTEGER"


class TestConfig:
    """Cấu hình test: SQLite in-memory, tắt tracking, JWT secret giả."""
    TESTING = True
    SQLALCHEMY_DATABASE_URI = "sqlite:///:memory:"
    SQLALCHEMY_TRACK_MODIFICATIONS = False
    JWT_SECRET_KEY = "test-jwt-secret-key-for-unit-testing-only"
    JWT_ACCESS_TOKEN_EXPIRES = False
    SECRET_KEY = "test-secret-key"
    # Cloudinary dummy (không thực sự upload trong test)
    CLOUDINARY_CLOUD_NAME = "test_cloud"
    CLOUDINARY_API_KEY = "test_key"
    CLOUDINARY_API_SECRET = "test_secret"


@pytest.fixture(scope="session")
def app():
    """
    Tạo Flask app với SQLite in-memory cho toàn bộ test session.
    Patch cloudinary và scheduler để tránh side effects.
    """
    # Patch cloudinary để tránh gọi thật
    with patch("cloudinary.config"), patch("app.extensions.scheduler.start"), \
         patch("app.extensions.scheduler.add_job"), patch("app.extensions.scheduler.init_app"):

        flask_app = Flask(__name__)
        flask_app.config.from_object(TestConfig)
        db.init_app(flask_app)

        # Import models để SQLAlchemy nhận biết các bảng
        with flask_app.app_context():
            from app.models.user_profile import UserProfile
            from app.models.user_profile_weight_history import UserProfileWeightHistory
            from app.models.daily_energy_log import DailyEnergyLog
            from app.models.food_record import FoodRecord

            db.create_all()

        # Push app context cho toàn session
        ctx = flask_app.app_context()
        ctx.push()

        yield flask_app

        db.drop_all()
        ctx.pop()


@pytest.fixture(scope="function")
def db_session(app):
    """
    Fixture DB với SAVEPOINT để rollback sau mỗi test.
    Đảm bảo mỗi test case chạy độc lập và DB quay về trạng thái trước test (Rollback).
    """
    # Tạo SAVEPOINT — rollback về điểm này sau khi test kết thúc
    nested = db.session.begin_nested()
    yield db.session
    # Rollback — phục hồi trạng thái DB trước test
    db.session.rollback()
    db.session.remove()
