# Tổng Quan Dự Án Healthcare

> Tài liệu này được tự động tổng hợp từ quét mã nguồn thư mục dự án.

---

## 1. Công Nghệ / Framework Đang Dùng

### Backend (BE)

| Thành phần                   | Công nghệ                                    | Phiên bản                    |
| ---------------------------- | -------------------------------------------- | ---------------------------- |
| **AuthService** (API chính)  | Spring Boot                                  | 3.5.6                        |
| **Gateway**                  | Spring Cloud Gateway                         | 2023.0.1 (Spring Boot 3.2.5) |
| **Ngôn ngữ Java**            | Java                                         | 21                           |
| **ORM / Persistence**        | Spring Data JPA + Hibernate                  | —                            |
| **Cơ sở dữ liệu**            | MySQL 8                                      | —                            |
| **Bảo mật**                  | Spring Security + JWT (JJWT) + OAuth2 Client | —                            |
| **Cache / Rate-limit**       | Redis (Spring Data Redis Reactive)           | —                            |
| **Email**                    | Spring Mail (SMTP) + SendGrid                | —                            |
| **Lưu trữ ảnh**              | Cloudinary                                   | —                            |
| **Thanh toán**               | VNPay (tích hợp thủ công)                    | —                            |
| **Tiện ích**                 | Lombok, Apache POI                           | —                            |
| **Food Detection Service**   | Flask + SQLAlchemy + Alembic                 | Python                       |
| **Skin Analyzer Service**    | Flask + SQLAlchemy + Alembic                 | Python                       |
| **Fitness Coach AI Service** | Flask + SQLAlchemy + LangChain/RAG           | Python                       |

### Frontend (FE)

| Thành phần           | Công nghệ                  | Phiên bản |
| -------------------- | -------------------------- | --------- |
| **Framework**        | React Native (Expo)        | ~54.0.30  |
| **Điều hướng**       | Expo Router (file-based)   | ~6.0.21   |
| **Quản lý state**    | Zustand                    | ^5.0.9    |
| **Fetching / Cache** | TanStack React Query       | ^5.90.12  |
| **HTTP Client**      | Axios                      | ^1.13.2   |
| **Form**             | React Hook Form + Zod      | —         |
| **Xác thực OAuth**   | expo-auth-session          | ^7.0.10   |
| **Thông báo**        | expo-notifications         | ^0.32.15  |
| **Charts**           | react-native-gifted-charts | ^1.4.70   |
| **Ngôn ngữ**         | TypeScript                 | ~5.9.2    |

---

## 2. Cấu Trúc Thư Mục Chính

```
srcCode/
├── BE/                                    # Toàn bộ backend
│   ├── docker-compose.yml                 # Orchester tất cả services
│   ├── gateway/                           # Spring Cloud Gateway (port 8080)
│   │   └── src/main/java/com/example/gateway/
│   │       ├── GatewayApplication.java
│   │       └── config/RateLimitConfig.java
│   │
│   ├── AuthService/                       # Spring Boot API chính (port 8081)
│   │   ├── pom.xml
│   │   └── src/main/java/com/example/AuthService/
│   │       ├── AuthServiceApplication.java
│   │       ├── config/                    # AppConfig, SecurityConfig, CloudinaryConfig
│   │       ├── controller/                # 13 REST controllers
│   │       ├── service/                   # 15 interfaces + impl/
│   │       ├── repository/                # 14 JPA repositories
│   │       ├── entity/                    # 16 JPA entities
│   │       ├── dto/                       # ~46 DTO (request/, response/, stats/)
│   │       ├── security/                  # JWT filter, OAuth2, UserDetails
│   │       │   └── jwt/                   # JwtService, JwtAuthFilter, JwtProperties
│   │       ├── otp/                       # OTP entity + service + repository
│   │       ├── mail/                      # EmailService (SMTP + SendGrid impls)
│   │       ├── spec/                      # JPA Specifications (dynamic filter)
│   │       ├── enums/                     # Domain enums
│   │       ├── job/                       # OrderCleanupJob (scheduled)
│   │       ├── startup/                   # RoleSeeder
│   │       ├── exception/                 # GlobalExceptionHandler
│   │       └── util/                      # VnPayUtil
│   │
│   ├── Food_Detection_Microservices/      # Flask service (port 5002)
│   │   └── app/
│   │       ├── controllers/
│   │       ├── services/
│   │       ├── models/
│   │       ├── extensions.py              # SQLAlchemy instance
│   │       └── migrations/               # Alembic
│   │
│   ├── Skin_Analyzer_Microservices/       # Flask service (port 5001)
│   │   └── app/
│   │       ├── controllers/
│   │       ├── services/
│   │       ├── services_AI/               # AI/ML inference
│   │       └── models/
│   │
│   ├── Fitness_Coach_AI/                  # Flask service (port 5003)
│   │   └── app/
│   │       ├── controllers/
│   │       ├── routes/
│   │       ├── services/
│   │       ├── models/
│   │       ├── memory/                    # Chat memory + repository
│   │       └── rag/                       # RAG pipeline
│   │
│   ├── docs/                              # Tài liệu kiểm thử (Markdown)
│   └── scripts/                          # Script hỗ trợ devops/test
│
└── FE/                                    # React Native (Expo)
    ├── app/
    │   ├── (tabs)/                        # Bottom-tab navigation
    │   ├── (screen)/                      # Stack screens
    │   └── _layout.tsx                    # Root layout
    ├── components/                        # UI components tái sử dụng
    ├── services/                          # axiosInstance, API calls
    ├── hook/                              # Custom React hooks
    └── scripts/                          # Test scripts (Appium/WebDriverIO)
```

---

## 3. Các Module / Chức Năng Chính

### BE — AuthService (Spring Boot)

| Module              | Chức năng                                                              |
| ------------------- | ---------------------------------------------------------------------- |
| **Auth**            | Đăng ký, đăng nhập, refresh token, OTP email, OAuth2 (Google/Facebook) |
| **User**            | Quản lý thông tin người dùng, cập nhật profile                         |
| **Drug**            | Quản lý danh mục thuốc (CRUD, tìm kiếm, lọc nâng cao)                  |
| **Order**           | Đặt hàng, quản lý trạng thái đơn, tự động hủy đơn hết hạn              |
| **Payment**         | Xử lý thanh toán, VNPay IPN callback                                   |
| **Prescription**    | Quản lý đơn thuốc                                                      |
| **ImportInvoice**   | Nhập kho / hóa đơn nhập hàng                                           |
| **Section**         | Phân mục/nhóm sản phẩm                                                 |
| **Schedule**        | Lịch nhắc uống thuốc                                                   |
| **AdminStatistics** | Thống kê doanh thu, đơn hàng, người dùng                               |
| **Image**           | Upload ảnh lên Cloudinary                                              |
| **Mail / OTP**      | Gửi email OTP xác thực                                                 |

### BE — Flask Microservices

| Service              | Chức năng                                                               |
| -------------------- | ----------------------------------------------------------------------- |
| **Food Detection**   | Nhận dạng thực phẩm từ ảnh, tính calo, nhật ký ăn uống hằng ngày        |
| **Skin Analyzer**    | Phân tích tình trạng da từ ảnh, lưu lịch sử phân tích                   |
| **Fitness Coach AI** | Chatbot tư vấn sức khỏe, lập kế hoạch ăn/tập, RAG từ dữ liệu dinh dưỡng |

### Gateway

| Chức năng                       | Ghi chú                                   |
| ------------------------------- | ----------------------------------------- |
| **Định tuyến theo path prefix** | Chuyển tiếp request đến đúng microservice |
| **Rate limiting**               | Giới hạn request qua Redis                |

### FE — React Native

| Module              | Màn hình chính                                   |
| ------------------- | ------------------------------------------------ |
| **Authentication**  | Đăng nhập, đăng ký, OTP                          |
| **Food Diary**      | Nhật ký ăn uống, nhận dạng món ăn qua ảnh        |
| **Activities**      | Theo dõi hoạt động thể chất                      |
| **Health Overview** | Tổng quan sức khỏe, biểu đồ calo                 |
| **Skin Analysis**   | Chụp/gửi ảnh da, xem kết quả phân tích           |
| **Drug Purchase**   | Duyệt danh mục thuốc, đặt hàng, thanh toán VNPay |
| **Notifications**   | Nhắc nhở uống thuốc, lịch trình                  |

---

## 4. Các Lớp Quan Trọng Theo Từng Module

### Module Auth

| Lớp                        | Loại       | Vai trò                                         |
| -------------------------- | ---------- | ----------------------------------------------- |
| `AuthController`           | Controller | Endpoint đăng nhập, đăng ký, refresh token, OTP |
| `AuthServiceImpl`          | Service    | Logic xác thực, tạo JWT, OTP flow               |
| `CustomUserDetailsService` | Security   | Load user từ DB cho Spring Security             |
| `JwtService`               | Security   | Tạo & xác thực JWT token                        |
| `JwtAuthFilter`            | Filter     | Interceptor kiểm tra Bearer token mỗi request   |
| `OtpService`               | Service    | Tạo/xác thực OTP qua email                      |
| `EmailOtpRepository`       | Repository | Lưu trữ OTP trong DB                            |
| `UserRepository`           | Repository | Truy vấn user theo email, username              |
| `User`                     | Entity     | Bảng `users`                                    |
| `EmailOtp`                 | Entity     | Bảng lưu OTP                                    |

### Module Drug (Thuốc)

| Lớp                  | Loại       | Vai trò                               |
| -------------------- | ---------- | ------------------------------------- |
| `DrugController`     | Controller | CRUD thuốc, tìm kiếm, lọc, upload ảnh |
| `DrugServiceImpl`    | Service    | Logic nghiệp vụ quản lý thuốc         |
| `DrugRepository`     | Repository | Query JPQL tùy chỉnh + Specification  |
| `DrugSpecifications` | Spec       | Bộ lọc động (tên, danh mục, giá…)     |
| `Drug`               | Entity     | Bảng `drugs`                          |
| `Section` / `Unit`   | Entity     | Danh mục & đơn vị                     |

### Module Order (Đơn hàng)

| Lớp                        | Loại       | Vai trò                                      |
| -------------------------- | ---------- | -------------------------------------------- |
| `OrderController`          | Controller | Tạo đơn, lấy danh sách, hủy đơn              |
| `AdminOrderController`     | Controller | Quản trị đơn hàng (duyệt, từ chối)           |
| `OrderServiceImpl`         | Service    | Xử lý đặt hàng, kiểm tra tồn kho             |
| `ScheduleAutoServiceImpl`  | Service    | Tự động hủy đơn hết hạn (scheduled job)      |
| `OrderCleanupJob`          | Job        | `@Scheduled` trigger cho ScheduleAutoService |
| `OrderRepository`          | Repository | JPQL thống kê, lọc theo trạng thái           |
| `OrderItemRepository`      | Repository | Chi tiết từng sản phẩm trong đơn             |
| `Order` / `OrderItem`      | Entity     | Bảng `orders`, `order_items`                 |
| `OrderStatsSpecifications` | Spec       | Lọc thống kê đơn hàng                        |

### Module Payment (Thanh toán)

| Lớp                  | Loại       | Vai trò                             |
| -------------------- | ---------- | ----------------------------------- |
| `PaymentController`  | Controller | Tạo URL thanh toán VNPay            |
| `VnPayIPNController` | Controller | Nhận IPN callback từ VNPay          |
| `PaymentServiceImpl` | Service    | Tạo hash VNPAY, cập nhật trạng thái |
| `PaymentRepository`  | Repository | Lưu/truy vấn Payment                |
| `VnPayUtil`          | Util       | Hàm tính HMAC-SHA512 VNPay          |
| `Payment`            | Entity     | Bảng `payments`                     |

### Module Prescription (Đơn thuốc)

| Lớp                                   | Loại       | Vai trò                                       |
| ------------------------------------- | ---------- | --------------------------------------------- |
| `PrescriptionController`              | Controller | CRUD đơn thuốc, gắn thuốc vào đơn             |
| `PrescriptionServiceImpl`             | Service    | Tạo đơn thuốc, liên kết `DrugInPrescription`  |
| `PrescriptionRepository`              | Repository | Truy vấn theo bác sĩ/bệnh nhân                |
| `DrugInPrescriptionRepository`        | Repository | Chi tiết thuốc trong đơn                      |
| `Prescription` / `DrugInPrescription` | Entity     | Bảng `prescriptions`, `drug_in_prescriptions` |

### Module Admin Statistics

| Lớp                          | Loại       | Vai trò                                 |
| ---------------------------- | ---------- | --------------------------------------- |
| `AdminStatisticsController`  | Controller | API thống kê doanh thu, người dùng      |
| `AdminStatisticsServiceImpl` | Service    | Dùng `EntityManager` + JPQL cho báo cáo |
| `OrderStatsSpecifications`   | Spec       | Filter động cho thống kê                |
| `PaymentStatsSpecifications` | Spec       | Filter động cho doanh thu               |

### Module User (Người dùng)

| Lớp                      | Loại       | Vai trò                              |
| ------------------------ | ---------- | ------------------------------------ |
| `UserController`         | Controller | Xem/cập nhật profile người dùng      |
| `AdminUserController`    | Controller | Admin quản lý tài khoản              |
| `UserServiceImpl`        | Service    | Logic cập nhật profile, đổi mật khẩu |
| `AuthProfileServiceImpl` | Service    | Đồng bộ profile sau OAuth2 login     |
| `UserRepository`         | Repository | Truy vấn user, phân trang            |
| `UserSpecification`      | Spec       | Filter admin user                    |
| `User`                   | Entity     | Bảng `users`                         |

### Flask — Food Detection

| Lớp                            | Loại       | Vai trò                             |
| ------------------------------ | ---------- | ----------------------------------- |
| `food_detection_controller.py` | Controller | Nhận ảnh, gọi AI nhận diện          |
| `calorie_controller.py`        | Controller | Tính calo từ kết quả nhận diện      |
| `daily_log_controller.py`      | Controller | Nhật ký ăn uống theo ngày           |
| `user_profile_controller.py`   | Controller | Mục tiêu calo cá nhân               |
| `calorie_service.py`           | Service    | Logic tính calo                     |
| `daily_log_service.py`         | Service    | CRUD nhật ký ăn                     |
| `user_profile_service.py`      | Service    | Quản lý profile dinh dưỡng          |
| `food_record.py`               | Model      | SQLAlchemy model `food_records`     |
| `daily_energy_log.py`          | Model      | SQLAlchemy model nhật ký năng lượng |
| `user_profile.py`              | Model      | SQLAlchemy model profile người dùng |

### Flask — Skin Analyzer

| Lớp                      | Loại       | Vai trò                             |
| ------------------------ | ---------- | ----------------------------------- |
| `analysis_controller.py` | Controller | Nhận ảnh da, trả kết quả phân tích  |
| `analysis_service.py`    | Service    | Điều phối AI + lưu kết quả          |
| `services_AI/`           | Service    | Inference model AI (skin condition) |
| `analysis_entity.py`     | Model      | SQLAlchemy `HealthAnalysis` entity  |

### Flask — Fitness Coach AI

| Lớp                       | Loại       | Vai trò                             |
| ------------------------- | ---------- | ----------------------------------- |
| `agent_controller.py`     | Controller | Nhận câu hỏi, trả lời chatbot       |
| `agent_service.py`        | Service    | Điều phối LangChain agent           |
| `meal_plan_service.py`    | Service    | Tạo kế hoạch ăn uống                |
| `workout_plan_service.py` | Service    | Tạo kế hoạch tập luyện              |
| `repository.py`           | Repository | `db.session.query(UserPlan)`        |
| `user_plan.py`            | Model      | SQLAlchemy `user_plans`             |
| `rag/`                    | RAG        | Vector store + retriever dinh dưỡng |
| `memory/`                 | Memory     | Lưu lịch sử hội thoại               |

---

## 5. Luồng Xử Lý Tổng Quát

### Spring Boot (AuthService)

```
Client (Mobile App)
    │
    ▼
[Gateway :8080]  ← Spring Cloud Gateway
    │  Routing theo path prefix
    │  Rate limiting via Redis
    │
    ▼
[AuthService :8081]
    │
    ├── JwtAuthFilter  ─── kiểm tra Bearer token ──► JwtService.validateToken()
    │                                                         │
    │                                                 UserRepository.findByEmail()
    │
    ▼
[Controller]  ─── @RestController, @RequestMapping
    │  Validate request DTO (@Valid)
    │  Gọi service
    │
    ▼
[Service Interface]
    │
    ▼
[ServiceImpl]  ─── @Service, @Transactional
    │  Business logic
    │  Gọi Repository / EntityManager
    │  Gọi các service phụ (EmailService, CloudinaryService…)
    │
    ▼
[Repository]  ─── extends JpaRepository<Entity, ID>
    │  findBy*, @Query JPQL
    │  JpaSpecification (filter động)
    │  EntityManager (báo cáo phức tạp)
    │
    ▼
[Entity]  ─── @Entity, @Table
    │  Hibernate → MySQL
    │
    ▼
[MySQL Database]
```

**Luồng đặc biệt — Thanh toán VNPay:**

```
OrderController.createOrder()
    → PaymentServiceImpl.createVnPayUrl()  [tạo HMAC-SHA512]
    → Trả URL redirect về client
    → Người dùng thanh toán trên VNPay
    → VnPayIPNController.vnpayIPN()  [nhận callback]
    → PaymentServiceImpl.handleIPN()  [cập nhật trạng thái]
```

**Luồng xác thực:**

```
POST /api/auth/login
    → AuthController.login()
    → AuthServiceImpl.authenticate()
    → CustomUserDetailsService.loadUserByUsername()
    → UserRepository.findByEmail()
    → JwtService.generateToken()  [tạo access + refresh token]
    ← Trả { accessToken, refreshToken }
```

### Flask Microservices (pattern chung)

```
Client
    │
    ▼
[Gateway :8080]
    │
    ▼
[Flask App :500x]
    │
    ├── Flask-JWT-Extended  ─── xác thực JWT
    │
    ▼
[Blueprint / Controller]  ─── @bp.route(...)
    │  Parse request, validate
    │
    ▼
[Service]  ─── logic nghiệp vụ + gọi AI model nếu cần
    │
    ▼
[SQLAlchemy Model / db.session]
    │  db.session.add(), db.session.commit()
    │  Model.query.filter_by(...)
    │
    ▼
[MySQL Database]  (shared database host trong Docker)
```

**Luồng đặc biệt — Food Detection:**

```
POST /api/v2/detect
    → food_detection_controller  [nhận ảnh multipart]
    → AI model inference  [nhận diện món ăn]
    → calorie_service.calculate()  [tra bảng dinh dưỡng]
    → daily_log_service.save()  [lưu vào daily_energy_log]
    ← Trả kết quả calo + thông tin dinh dưỡng
```

**Luồng đặc biệt — Fitness Coach AI (RAG):**

```
POST /api/v3/chat
    → agent_controller
    → agent_service  [LangChain agent]
    → rag/  [tìm kiếm vector store dinh dưỡng]
    → memory/repository  [lấy lịch sử hội thoại]
    → LLM  [sinh câu trả lời]
    → memory/repository.save()  [lưu turn mới]
    ← Trả câu trả lời chatbot
```

---

## 6. Các Điểm Truy Cập Cơ Sở Dữ Liệu

### Spring Boot — AuthService

| Điểm truy cập           | Cơ chế                          | Vị trí                                                                                                                    |
| ----------------------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| **14 JPA Repositories** | `JpaRepository<Entity, ID>`     | `repository/`                                                                                                             |
| **JPQL tùy chỉnh**      | `@Query("SELECT ...")`          | `OrderRepository`, `DrugRepository`, `ImportInvoiceDetailRepository`, `OrderItemRepository`, `ScheduleRepository`         |
| **EntityManager**       | `EntityManager.createQuery()`   | `AdminStatisticsServiceImpl` — báo cáo/thống kê phức tạp                                                                  |
| **JPA Specifications**  | `JpaSpecificationExecutor<T>`   | `DrugSpecifications`, `OrderSpecification`, `OrderStatsSpecifications`, `PaymentStatsSpecifications`, `UserSpecification` |
| **OTP Repository**      | `JpaRepository<EmailOtp, Long>` | `otp/EmailOtpRepository`                                                                                                  |

**Cấu hình kết nối JDBC:**  
`AuthService/src/main/resources/application.properties`

```properties
spring.datasource.url=jdbc:mysql://...
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=...MySQL8Dialect
```

### Flask — Food Detection Microservice

| Điểm truy cập                 | Cơ chế              | Vị trí                                                                                                                   |
| ----------------------------- | ------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| `db.session.add/commit/query` | Flask-SQLAlchemy    | `services/daily_log_service.py`, `services/user_profile_service.py`, `services/calorie_service.py`                       |
| SQLAlchemy models             | `db.Model` subclass | `models/food_record.py`, `models/daily_energy_log.py`, `models/user_profile.py`, `models/user_profile_weight_history.py` |
| Schema migrations             | Alembic             | `migrations/`                                                                                                            |

### Flask — Skin Analyzer Microservice

| Điểm truy cập          | Cơ chế           | Vị trí                         |
| ---------------------- | ---------------- | ------------------------------ |
| `db.session`           | Flask-SQLAlchemy | `services/analysis_service.py` |
| `HealthAnalysis` model | `db.Model`       | `models/analysis_entity.py`    |
| Schema migrations      | Alembic          | `migrations/`                  |

### Flask — Fitness Coach AI Microservice

| Điểm truy cập                | Cơ chế           | Vị trí                 |
| ---------------------------- | ---------------- | ---------------------- |
| `db.session.query(UserPlan)` | Flask-SQLAlchemy | `memory/repository.py` |
| `UserPlan` model             | `db.Model`       | `models/user_plan.py`  |

### Gateway

> Gateway **không truy cập database** trực tiếp. Redis chỉ dùng để lưu trạng thái rate-limiting.

---

## 7. Unit Testing — Phạm Vi Đã Kiểm Thử

### 7.1. Có cần OpenAI API key khi chạy unit test không?

**Không.** Các script unit test hiện tại **không gọi OpenAI thật**:

- **Fitness Coach AI:** Trong `tests/conftest.py`, module `openai` (và tài nguyên nặng liên quan) được nạp mock qua `sys.modules` _trước_ khi import `app`. Test chỉ kiểm thử lớp `UserStateRepositoryImpl` với **SQLite in-memory** — không dùng LLM.
- **AuthService (Java):** Dùng Mockito, không gọi dịch vụ ngoài. Context test Spring Boot tải MySQL từ cấu hình mặc định nếu chạy cả lớp `AuthServiceApplicationTests` — muốn tránh cần MySQL, có thể tắt/loại test đó hoặc cấu hình profile tách. **Các test service riêng lẻ (Auth/User/Drug/Jwt/Otp) vẫn chạy độc lập, không cần OpenAI.**
- **Food Detection / Skin Analyzer:** Không tích hợp OpenAI. Test dùng SQLite in-memory; các phụ thuộc AI nặng (onnx, torch, ultralytics, …) được **mock ở conftest** để không cần cài đủ môi trường như lúc chạy app thật.

> **Khi cần API key thật:** Khi bạn chạy **ứng dụng** (Docker hoặc `flask run` / gọi endpoint chat) chứ **không phải** khi chạy bộ unit test mô tả ở đây.

### 7.2. Tổng quan số lượng test (theo bộ mã nguồn hiện tại)

| Nơi chạy                          | Số test (khoảng)                        | Công cụ / báo cáo                                   |
| --------------------------------- | --------------------------------------- | --------------------------------------------------- |
| `BE/AuthService` (JUnit)          | 28 (gồm 1 bài load context Spring Boot) | `mvn test`, JaCoCo: `target/site/jacoco/index.html` |
| `BE/Food_Detection_Microservices` | 11                                      | `python -m pytest tests/`; Coverage.py              |
| `BE/Skin_Analyzer_Microservices`  | 4                                       | tương tự                                            |
| `BE/Fitness_Coach_AI`             | 3                                       | tương tự                                            |
| **Tổng backend unit test**        | **~46**                                 | Báo cáo Excel: `Unit_Testing_Report.xlsx` (nếu có)  |

Gợi ý: script gốc mã nguồn `run_tests.ps1` (cần JDK 21 + Python đã cài phụ thuộc) để chạy từng phần.

### 7.3. So khớp phạm vi mục 1.2 (Scope of Testing) — trạng thái hiện tại

Bảng dưới đây đối chiếu **phạm vi đã khai báo trong báo cáo / mục 1.2** với **mã test thực tế trong repo** (để README và `Unit_Testing_Report.xlsx` cùng một “nguồn sự thật”).

#### 7.3.1. Phạm vi **ĐƯỢC** kiểm thử (theo mục 1.2)

| Hạng mục 1.2         | Lớp / vị trí thực tế trong code                                                                               | Trạng thái                 | Ghi chú ngắn                                                                                                  |
| -------------------- | ------------------------------------------------------------------------------------------------------------- | -------------------------- | ------------------------------------------------------------------------------------------------------------- |
| AuthServiceImpl      | `AuthServiceImpl`                                                                                             | **Đạt**                    | `AuthServiceImplTest`                                                                                         |
| UserProfileService   | Trong AuthService không có tên riêng `UserProfileService`; nghiệp vụ user/profile nằm ở **`UserServiceImpl`** | **Một phần / tương đương** | `UserServiceImplTest` — nên thống nhất tên trong báo cáo (UserService vs UserProfileService)                  |
| CalorieService       | `CalorieService` (Flask)                                                                                      | **Đạt**                    | `tests/test_calorie_service.py`                                                                               |
| FoodDetectionService | `app/services_AI/` (vd. `FoodDetectionService` / `food_detection_services.py`)                                | **Chưa**                   | Chưa có unit test cho pipeline nhận diện ảnh; chỉ test **calorie + daily log**                                |
| AnalysisService      | `AnalysisService` (Flask)                                                                                     | **Đạt**                    | `tests/test_analysis_service.py`                                                                              |
| FitnessAIService     | Agent / LLM (`AgentService`, route chat, …); persistence plan                                                 | **Một phần**               | Chỉ có **`UserStateRepositoryImpl`** (`test_repository.py`), **chưa** test toàn bộ “Fitness AI service” / LLM |
| PrescriptionService  | `PrescriptionServiceImpl`                                                                                     | **Chưa**                   | Chưa có file test tương ứng                                                                                   |
| OrderService         | `OrderServiceImpl`                                                                                            | **Chưa**                   | Chưa có file test tương ứng                                                                                   |
| DrugService          | `DrugServiceImpl`                                                                                             | **Đạt**                    | `DrugServiceImplTest`                                                                                         |

**Ngoài danh sách 1.2** (bổ sung có giá trị, không mâu thuẫn phạm vi “KHÔNG test”): `JwtServiceTest`, `OtpServiceTest`, và một bài **load context** Spring Boot (`AuthServiceApplicationTests`).

#### 7.3.2. Phạm vi **KHÔNG** cần unit test (theo mục 1.2)

| Hạng mục 1.2                                  | Trạng thái so với repo                                                                                           |
| --------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Controller thuần mapping                      | **Đúng hướng** — chưa viết test controller trong bộ unit hiện tại                                                |
| Repository / ORM mapping thuần                | **Đúng hướng** — Java dùng mock repository; Python test DB qua service + SQLite, không test “mapping-only” riêng |
| Logic nội bộ VNPay, Google OAuth2, Cloudinary | **Đúng hướng** — không có unit test sâu các tích hợp này                                                         |
| Kiến thức nội tại LLM                         | **Đúng hướng** — không test chất lượng câu trả lời LLM                                                           |
| Code train mô hình                            | **Đúng hướng** — ngoài phạm vi                                                                                   |
| File cấu hình / DTO / entity đơn giản         | **Đúng hướng** — không test riêng                                                                                |

> **Tóm tắt:** So với bảng “ĐƯỢC kiểm thử” trong 1.2, repo **chưa đủ** cho **OrderService**, **PrescriptionService**, **FoodDetectionService**, và phần lớn **FitnessAIService**; phần còn lại **đạt hoặc tương đương / một phần** như bảng trên. Có thể **bổ sung test** hoặc **thu hẹp / sửa lại mục 1.2** trong Excel cho khớp thực tế.

### 7.4. Các hàm / lớp đã được bao phủ bởi unit test

#### AuthService (Java)

| Tệp test                      | Lớp/đích được test | Tình huống chính (theo mã / TC ID)                                                                                                                |
| ----------------------------- | ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AuthServiceImplTest`         | `AuthServiceImpl`  | `login` (hợp lệ, sai mật khẩu, tài khoản khóa, admin view, cấm user thường), `registerStart` (gửi OTP, email trùng), `resetPassword` (OTP hợp lệ) |
| `UserServiceImplTest`         | `UserServiceImpl`  | `getUserProfileByEmail`, `updateUser` (trùng email), `createUser`, `deleteUser`, `updateMyProfile`                                                |
| `DrugServiceImplTest`         | `DrugServiceImpl`  | `getDrugs` (user vs admin), `updateDrugActive`, `deleteDrug`, `suggestNames`                                                                      |
| `JwtServiceTest`              | `JwtService`       | Tạo token, `isValid` (còn hạn / hết hạn, có clock skew), `extractUsername` (lowercase)                                                            |
| `OtpServiceTest`              | `OtpService`       | `sendOtp`, `verify` (đúng/sai/không có OTP active)                                                                                                |
| `AuthServiceApplicationTests` | Boot context       | Nạp ngữ cảnh Spring Boot toàn ứng dụng (phụ thuộc cấu hình DB)                                                                                    |

#### Food Detection (Python)

| Tệp test                    | Hàm / lớp được test                             | Nội dung                                                                                                                                      |
| --------------------------- | ----------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `test_calorie_service.py`   | `CalorieService`                                | Thêm bản ghi ăn (tạo/cộng `DailyEnergyLog`), thêm nhiều món, cập nhật bản ghi (403 khi user khác), xóa bản ghi, lấy danh sách khi chưa có log |
| `test_daily_log_service.py` | `calculate_bmr_from_metrics`, `DailyLogService` | Tính BMR nam/nữ/thiếu dữ liệu; `update_daily_steps` thành công / lỗi khi thiếu profile                                                        |

#### Skin Analyzer (Python)

| Tệp test                   | Lớp được test     | Nội dung                                                                                                   |
| -------------------------- | ----------------- | ---------------------------------------------------------------------------------------------------------- |
| `test_analysis_service.py` | `AnalysisService` | `save_analysis_from_request`, `update_doctor_note` (đúng user / user khác), `get_history_by_user` (thứ tự) |

#### Fitness Coach AI (Python)

| Tệp test             | Lớp / phương thức         | Nội dung                                                                                                 |
| -------------------- | ------------------------- | -------------------------------------------------------------------------------------------------------- |
| `test_repository.py` | `UserStateRepositoryImpl` | `get_state` (chưa có plan), `save_state` (insert mới, update bản ghi cũ) trên bảng `user_plans` (SQLite) |

> **Lưu ý phạm vi:** Unit test tập trung tầng **service / repository** và thuật toán. **Controller**, **một số job**, **dịch vụ AI/ảnh thật** thường nằm ngoài phạm vi hoặc cần integration/E2E riêng.

### 7.5. Bằng chứng thực thi (screenshot) — lệnh đã dùng và cách làm báo cáo

Phần này trả lời hai yêu cầu thường gặp trong báo cáo: **(1) tóm tắt kết quả chạy test + ảnh chụp**, **(2) coverage + ảnh chụp** (Coverage.py cho PyTest, JaCoCo cho Java).

#### Các lệnh đã chạy khi kiểm tra (tham khảo)

**Java — AuthService (Maven + JUnit):**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
cd D:\School\ki2_nam4\DBCLPM\srcCode\BE\AuthService
mvn test
```

Kết quả mong đợi ở cuối log: dòng dạng `[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0` và `[INFO] BUILD SUCCESS`. **Chụp màn hình** cửa sổ PowerShell/Terminal phần cuối log (có số pass/fail rõ ràng).

**Python — từng microservice (pytest + Coverage.py), ví dụ Food Detection:**

```powershell
cd D:\School\ki2_nam4\DBCLPM\srcCode\BE\Food_Detection_Microservices
python -m coverage erase
python -m coverage run -m pytest tests/ -v
python -m coverage report
python -m coverage html -d htmlcov
```

Lặp lại tương tự trong thư mục `BE\Skin_Analyzer_Microservices` và `BE\Fitness_Coach_AI` (đổi `-d htmlcov` nếu muốn thư mục tên khác cho từng service).

#### (1) Execution Report — bạn cần làm gì để có ảnh + số pass/fail

1. Mở **PowerShell** hoặc **Terminal** trong VS Code / Cursor.
2. Chạy đúng các lệnh ở trên (Java một lần; Python từng thư mục `tests/`).
3. **Chụp màn hình** (`Win + Shift + S` hoặc Snipping Tool):
   - Ảnh 1: phần cuối log Maven có `Tests run: … Failures: …` và `BUILD SUCCESS` / `BUILD FAILURE`.
   - Ảnh 2–4: từng service Python có dòng `X passed` / `FAILED` và bảng tóm tắt nếu có.
4. Trong Word/Excel/PDF báo cáo, viết một đoạn tóm tắt, ví dụ: _“AuthService: 28 test, 0 fail. Food Detection: 11 passed. Skin: 4 passed. Fitness AI: 3 passed.”_ (số liệu lấy đúng từ log lần chạy của bạn).

Nếu muốn một lệnh gom nhiều phần, có thể dùng script `run_tests.ps1` ở thư mục gốc `srcCode` (cần JDK 21 và Python đã cài pytest/coverage), rồi **chụp màn hình** output của script.

#### (2) Code Coverage Report — bạn cần làm gì để có ảnh từ công cụ

**Python (Coverage.py):**

1. Sau `coverage run` + `coverage html -d htmlcov`, mở file trong trình duyệt:
   - Food: `BE\Food_Detection_Microservices\htmlcov\index.html`
   - Skin: `BE\Skin_Analyzer_Microservices\htmlcov\index.html` (sau khi bạn tạo)
   - Fitness: `BE\Fitness_Coach_AI\htmlcov\index.html` (sau khi bạn tạo)
2. **Chụp màn hình** trang tổng quan (có tỷ lệ % từng file) — đó là bằng chứng coverage theo đúng yêu cầu “Coverage.py cho PyTest”.
3. (Tuỳ chọn) Chụp thêm một file `.py` cụ thể khi click vào để thấy dòng nào được cover.

**Java (JaCoCo — tạo khi chạy `mvn test` vì plugin đã cấu hình trong `pom.xml`):**

1. Mở trong trình duyệt: `BE\AuthService\target\site\jacoco\index.html`
2. **Chụp màn hình** trang index (tổng % coverage) và có thể thêm một package (vd. `service.impl`) để minh họa.

**Tóm tắt trong báo cáo:** Ghi rõ công cụ (Coverage.py / JaCoCo), ngày chạy, lệnh đã dùng, đính kèm ảnh; với Coverage.py có thể trích thêm bảng text từ `coverage report` (cột `Cover`, `TOTAL`).

---

## Ghi Chú Bảo Mật

> **Cảnh báo:** File `AuthService/src/main/resources/application.properties` và các file `config.py` của Flask chứa thông tin nhạy cảm (JWT secret, mật khẩu DB, API key VNPay, Cloudinary, OAuth). Cần chuyển sang biến môi trường hoặc secret manager trước khi đưa lên repository công khai.

---

_Tài liệu được tạo tự động; mục 7 (Unit Testing) cập nhật theo bộ test unit trong mã nguồn — cập nhật lại nếu thay đổi kiến trúc hoặc phạm vi test._
