# Lapzone Web - Đồ án Website Bán Laptop (Java Spring Boot)

Lapzone Web là một ứng dụng thương mại điện tử chuyên cung cấp thiết bị máy tính (laptop), được xây dựng trên nền tảng Java Spring Boot với kiến trúc MVC và sử dụng Thymeleaf để render giao diện.

## 🚀 Công Nghệ Sử Dụng

*   **Backend:** Java 25, Spring Boot 4.x
*   **Database:** MySQL
*   **ORM:** Spring Data JPA (Hibernate)
*   **Frontend:** HTML, CSS, Thymeleaf
*   **Build Tool:** Maven

## 📋 Yêu Cầu Môi Trường (Prerequisites)

Để chạy được dự án này, máy tính của bạn cần cài đặt sẵn:
1.  **JDK 25** (Java Development Kit 25).
2.  **MySQL Server** (và một công cụ quản lý như MySQL Workbench, XAMPP, hoặc Navicat).
3.  **IDE** (như IntelliJ IDEA, Eclipse, hoặc VS Code).

## ⚙️ Hướng Dẫn Cài Đặt & Chạy Chương Trình

### Bước 1: Khởi tạo Cơ sở dữ liệu (Database)

1. Mở công cụ quản lý MySQL của bạn (VD: MySQL Workbench).
2. Tạo một database mới với tên `lapzone_db`:
   ```sql
   CREATE DATABASE lapzone_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
3. Mở và chạy file script `lapzone_init_official.sql` (nằm ở thư mục gốc của dự án) vào database `lapzone_db` vừa tạo để tạo bảng và nạp dữ liệu mẫu.

### Bước 2: Cấu hình kết nối Database

Nếu chưa có cấu hình cục bộ, sao chép `src/main/resources/application-example.properties` thành `src/main/resources/application.properties`. File cục bộ này đã được `.gitignore` loại trừ; không commit thông tin đăng nhập.

Đặt biến môi trường trong PowerShell trên Windows trước khi chạy ứng dụng:

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<mật khẩu MySQL cục bộ của bạn>"
$env:GEMINI_API_KEY = "<API key Gemini cục bộ của bạn>"
```

Trong VS Code, mở **Terminal → New Terminal** (chọn PowerShell nếu cần), chạy các lệnh trên trong terminal đó, rồi chạy ứng dụng ở Bước 3. Các biến chỉ có hiệu lực với terminal/ứng dụng được mở từ terminal đó. Không lưu giá trị thật vào README, `launch.json` đã chia sẻ, hoặc Git.
### Bước 3: Chạy ứng dụng

Bạn có thể chạy dự án bằng một trong các cách sau:

**Cách 1: Chạy bằng IDE (Khuyên dùng)**
- Tìm file `LapzonewebApplication.java` trong thư mục `src/main/java/com/lapzone/lapzoneweb/`.
- Chuột phải vào file và chọn **Run 'LapzonewebApplication.main()'**.

**Cách 2: Chạy bằng Terminal/Command Line (Maven)**
- Mở terminal/cmd và trỏ đường dẫn vào bên trong thư mục `lapzoneweb`.
- Chạy lệnh sau:
  ```bash
  ./mvnw spring-boot:run
  ```
  *(Trên Windows, bạn có thể chạy `mvnw.cmd spring-boot:run`)*

### Bước 4: Truy cập ứng dụng

Sau khi console báo khởi động thành công (dòng chữ `Started LapzonewebApplication in ... seconds`), hãy mở trình duyệt và truy cập:

*   **Trang chủ khách hàng:** [http://localhost:8080](http://localhost:8080)
*   *(Nếu có trang quản trị admin, hãy điều hướng từ giao diện hoặc vào đường dẫn map với AdminController)*

## 📂 Cấu Trúc Thư Mục Chính

```text
lapzoneweb/                     # Thư mục chứa Source Code Java
    ├── src/main/java/com/lapzone/lapzoneweb/
    │   ├── config/                 # Cấu hình hệ thống
    │   ├── controller/             # Các lớp điều khiển (Tiếp nhận request)
    │   ├── model/
    │   │   ├── entity/             # Các thực thể (Map với bảng trong Database)
    │   │   ├── repository/         # Các interface truy vấn Database (JPA)
    │   │   └── service/            # Lớp xử lý nghiệp vụ (Business Logic)
    │   └── LapzonewebApplication.java # File chạy chính của chương trình
    │
    └── src/main/resources/
        ├── static/                 # Chứa CSS, JS, Images tĩnh
        ├── templates/              # Chứa các file giao diện HTML (Thymeleaf)
        └── application.properties  # File cấu hình môi trường
```
