# ClipperCast

Ứng dụng Android: chia sẻ bài viết từ Chrome → đọc sạch offline → nghe như podcast bằng giọng đọc của máy. Không tài khoản, không máy chủ, không quảng cáo.

Đặc tả sản phẩm: [docs/clippercast_spec_v2.md](docs/clippercast_spec_v2.md)

## Cài thử

Mỗi lần đẩy code lên nhánh `main`, GitHub Actions build file APK. Vào tab **Actions** → lần chạy mới nhất → tải **clippercast-apk**, giải nén và cài `app-debug.apk` lên điện thoại (Android 8.0 trở lên).

## Build trên máy

Cần Android Studio (hoặc JDK 17 + Android SDK 35):

```
./gradlew assembleDebug
```

## Cấu trúc

| Thư mục | Nội dung |
|---|---|
| `share/` | Nhận link từ Share Sheet (2 lựa chọn: Lưu, Nghe ngay) |
| `clip/` | Chuẩn hóa link, bóc tách 2 tầng (OkHttp + Readability4J, WebView + Readability.js), tải ảnh, WorkManager |
| `data/` | Room (FTS4, tìm không dấu), DataStore cài đặt |
| `reader/` | Trình đọc WebView: 5 bảng màu, font nhúng, chia câu để highlight |
| `player/` | TTS + Media3 (thông báo, màn hình khóa, tai nghe), hàng chờ, hẹn giờ tắt |
| `ui/` | Compose: Hộp thư, Hàng chờ nghe, Lưu trữ, Tìm kiếm, Cài đặt, Onboarding, Thùng rác, trình duyệt trong app |
| `backup/` | Sao lưu/khôi phục ZIP, xuất Markdown |

## Chưa làm trong bản này

Gói Pro và Google Play Billing, nhập từ Pocket/Instapaper, highlight và ghi chú, widget, Android Auto, giao diện tiếng Anh. Xem lộ trình ở mục 9 của đặc tả.
