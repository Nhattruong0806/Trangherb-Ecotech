# Trangherb-Ecotech

## Backend (Spring Boot)

Yêu cầu: Java 17+, Maven.

Thiết lập biến môi trường:
- `GEMINI_API_KEY` (bắt buộc)
- `GEMINI_MODEL` (tuỳ chọn, mặc định `gemini-2.0-flash-lite`)
 - `DOCX_PATHS` (tuỳ chọn, ngăn cách `;`, mặc định `_Dự án hoàn chỉnh (1) (1).docx;Hệ sinh thái tuần hoàn thông minh (1).docx`)
- `PORT` (tuỳ chọn, mặc định `3000`)

Chạy server:

```bash
mvn spring-boot:run
```

Mở `http://localhost:3000` để dùng chatbot.
