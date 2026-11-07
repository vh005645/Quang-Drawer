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


## Phiên bản 2.2.1 - tên gói tungbui.drawer
- Đổi tên gói về `tungbui.drawer`, activity `.MainActivity` (khớp lệnh cũ `set-home-activity tungbui.drawer/.MainActivity`). Chức năng như 2.2.0.

## Phiên bản 2.2.0 - bố cục theo ảnh TV mẫu, nhẹ RAM
- Đồng hồ + ngày góc trái trên; góc phải trên: Nguồn vào (Input), Wi-Fi, Cài đặt.
- 2 hàng x 9 thẻ app nhỏ (16:9) ở dưới; hơn 18 app thì cuộn xuống.
- Đổi hình nền: nhấn giữ nút Cài đặt -> "Đổi hình nền" (hoặc chép ảnh vào Android/data/tungbui.drawer/files/wallpaper.jpg).
  Ảnh được cắt/thu nhỏ đúng bằng màn hình và lưu 1 file; chỉ giữ 1 bitmap RGB_565 (~4 MB).
- Icon/banner chỉ tải khi hiện ra (bộ nhớ đệm nhỏ), không giữ icon của mọi app.
- Nhấn giữ OK trên app: Di chuyển (mũi tên đổi chỗ, OK xong) / Ẩn - Hiện lại. Nhấn giữ nút Cài đặt -> "Hiện các app đã ẩn".

## Phiên bản 2.1.0 - một hàng ứng dụng ngang
- Ứng dụng xếp 1 hàng ngang ở dưới cùng, bấm trái/phải để chọn; thẻ và icon nhỏ gọn cân đối.
- Góc phải trên: nút Cài đặt (cài đặt gốc của TV) và nút "hiện app đã ẩn".
- Nhấn giữ Enter/OK trên một app: menu "Di chuyển vị trí" (trái/phải đổi chỗ, OK để xong) và "Ẩn / Hiện lại ứng dụng".
  Thứ tự và danh sách ẩn được lưu lại.

## Phiên bản 2.0.0 - giao diện mới, tên gói tungbui.drawer
- Giao diện mới: nền gradient tối, đồng hồ + ngày ở đầu trang, lưới thẻ 16:9 bo góc (có banner thì hiện banner, không thì icon + tên),
  ô đang chọn có viền trắng và phóng to nhẹ. Lưới tự cập nhật khi cài / gỡ ứng dụng.
- Tên gói đổi thành `tungbui.drawer` (cả debug và release, không còn hậu tố `.AppDrawer`).
- Giữ nguyên: là launcher mặc định (HOME), Back không thoát, không hiện chính nó, mỗi lần hiện ra gọi TV Guard
  (`com.tvguard.app/.BootTrigger`) tối đa 1 lần / 2 phút.
- Mới: cùng lượt đó gọi thêm `tb.voice.mstore` và `tungbui.mitv.cstore` (cách nhau 1 giây, sau TV Guard).
  Danh sách nằm ở `EXTRA_WAKE` trong `MainActivity.java`; muốn chỉ định rõ màn hình khởi động thì điền cột thứ 2 dạng `ten.goi/.TenLop`.

Đặt làm launcher mặc định (sau khi cài):
`adb shell cmd package set-home-activity tungbui.drawer/.MainActivity`
