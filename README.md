# AppDrawer TV

Ứng dụng danh sách ứng dụng (App Drawer) đơn giản dành cho Android TV, được tùy chỉnh với giao diện Tiếng Việt.

## Tính năng
- Hiển thị toàn bộ các ứng dụng đã cài đặt trên thiết bị.
- Giao diện tối giản, phù hợp cho màn hình TV.
- Ngôn ngữ: Tiếng Việt.

## Tải về phiên bản mới nhất
Bạn có thể tải về tệp APK trực tiếp từ liên kết dưới đây:

👉 **[Tải xuống AppDrawer.apk](https://github.com/daivietpda/AppDrawerTV/raw/master/AppDrawer.apk)**

### Cài đặt nhanh qua Downloader App
Nếu bạn sử dụng ứng dụng **Downloader** trên Android TV, hãy nhập mã code sau để tải nhanh:
👉 **Code: `8297116`**

## Hình ảnh ứng dụng
*Tiêu đề "Tất Cả APP" đã được căn giữa và biểu tượng ứng dụng đã được cập nhật mới.*

---
*Phát triển bởi daivietpda.*


## Phiên bản 2.0.0 - giao diện mới, tên gói quang.drawer
- Giao diện mới: nền gradient tối, đồng hồ + ngày ở đầu trang, lưới thẻ 16:9 bo góc (có banner thì hiện banner, không thì icon + tên),
  ô đang chọn có viền trắng và phóng to nhẹ. Lưới tự cập nhật khi cài / gỡ ứng dụng.
- Tên gói đổi thành `quang.drawer` (cả debug và release, không còn hậu tố `.AppDrawer`).
- Giữ nguyên: là launcher mặc định (HOME), Back không thoát, không hiện chính nó, mỗi lần hiện ra gọi TV Guard
  (`com.tvguard.app/.BootTrigger`) tối đa 1 lần / 2 phút.
- Mới: cùng lượt đó gọi thêm `tb.voice.mstore` và `tungbui.mitv.cstore` (cách nhau 1 giây, sau TV Guard).
  Danh sách nằm ở `EXTRA_WAKE` trong `AppDrawerActivity.java`; muốn chỉ định rõ màn hình khởi động thì điền cột thứ 2 dạng `ten.goi/.TenLop`.

Đặt làm launcher mặc định (sau khi cài):
`adb shell cmd package set-home-activity quang.drawer/.AppDrawerActivity`
