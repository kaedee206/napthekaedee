# Kế hoạch triển khai Plugin Nạp Thẻ (Tích hợp SePay & TheSieuRe)

Kế hoạch này phác thảo cấu trúc và quy trình để xây dựng một plugin Minecraft tích hợp nạp thẻ cào và chuyển khoản ngân hàng hoàn toàn tự động, bao gồm quy đổi tiền tệ, phần thưởng theo mốc, và lịch sử nạp. 

*Bản kế hoạch đã được cập nhật với các lựa chọn công nghệ cụ thể của máy chủ.*

## ⚠️ User Review Required

Dưới đây là xác nhận về kiến trúc sau khi nhận được phản hồi của bạn. Bạn hãy kiểm tra lại một lần nữa xem luồng xử lý đã đúng ý bạn chưa trước khi tiến hành code.

1. **Cơ sở dữ liệu**: Sử dụng **SQLite**. Plugin sẽ tự động tạo file database trong thư mục của plugin để lưu trữ dữ liệu an toàn, chống lag và hỗ trợ tính năng bảng xếp hạng.
2. **Web Server & IPN (Cloudflare Tunnel)**: Plugin sẽ có một Embedded HTTP Server chạy ở một port cụ thể. Bạn sẽ dùng Cloudflare Tunnel để trỏ một tên miền vào port này. Địa chỉ này sẽ được dùng để đăng ký Webhook trên SePay và Callback trên TheSieuRe.
3. **Hiển thị QR Code**: Dùng API của **vietqr.io**, trả về ảnh QR, vẽ lên Map và hiển thị In-game.
4. **Hệ thống tiền tệ**: Sử dụng **Vault 2.0** (phiên bản trên Modrinth).
5. **Cổng Thẻ Cào**: Cổng **TheSieuRe**. Plugin sẽ bắn API đến TheSieuRe để nạp và lắng nghe callback trả về.

## Proposed Changes & Architecture

### 1. Cấu trúc Cấu hình (Configurations)

Sẽ có 3 file config chính:

#### `config.yml`
```yaml
# Cấu hình Web Server (nhận callback TheSieuRe và SePay)
web-server:
  port: 8080

# Cấu hình TheSieuRe
thesieure:
  partner_id: "your_partner_id"
  partner_key: "your_partner_key"

# Cấu hình Tỷ giá (Exchange Rates)
rates:
  qr_rate: 1.0 # 10k VND = 1 Crystal
  card_rate: 0.8 # 10k VND = 0.8 Crystal
  crystal_to_koin_rate: 100000 # 1 Crystal = 100k Koin (Vault)
  wrong_value_penalty: 0.5 # Phạt 50% nếu sai mệnh giá thẻ
```

#### `reward.yml`
Quản lý các mốc nạp tích lũy. Hệ thống sẽ tự động check mỗi khi có giao dịch thành công.
```yaml
milestones:
  rank1:
    amount: 100000 # Tổng nạp 100k
    commands:
      - "lp user %player% parent add rank1"
      - "broadcast &aNgười chơi %player% đã đạt mốc nạp Rank 1!"
  rank2:
    amount: 200000
    commands:
      - "lp user %player% parent add rank2"
```

### 2. Các module xử lý (Java Packages)

#### `com.kaedee.napthe.http`
- Chứa một lightweight HTTP Server (`HttpServer` của Java) lắng nghe tại `/payment/sepay` và `/payment/tsr`.
- **Logic xử lý IPN SePay**: 
  1. Kiểm tra header `X-Secret-Key`.
  2. Parse JSON body lấy `order_invoice_number` chứa UUID/Tên người chơi.
  3. Lên lịch task chạy ở Main Thread để cộng Crystal (tỉ lệ x1.0) và Koin.
- **Logic xử lý Callback TheSieuRe**:
  1. Nhận thông tin callback từ TSR (thành công/sai mệnh giá/thất bại).
  2. Kiểm tra chữ ký MD5/SHA256 theo tài liệu của TSR.
  3. Cộng Crystal (tỉ lệ x0.8). Nếu sai mệnh giá phạt thêm 50% (còn x0.4).

#### `com.kaedee.napthe.commands`
- **`/napthe`**: Mở GUI hoặc hội thoại chat (Chat Conversation) để người chơi chọn:
  - Chọn cổng (Thẻ Cào / Ngân Hàng).
  - Nhập số tiền (hỗ trợ `5k, 10k, 20k...`).
- **`/napthe adhistory [player]`**: Admin xem lịch sử của mọi người (phân trang, Adventure API TextComponent).
- **`/napthe history`**: Người chơi xem lịch sử bản thân.
- **`/topnap`**: Truy xuất Database để lấy top 10 tổng nạp.
- **`/napinfo`**: Truy xuất thông tin (Rank hiện tại, IP, Tổng nạp, 5 giao dịch gần nhất). Nút click để xem toàn bộ.

#### `com.kaedee.napthe.map`
- Lớp `QRMapRenderer`: Ghi đè `MapRenderer` mặc định.
- Khi user chọn QR, request API `vietqr.io` với số tiền và nội dung chuyển khoản là Username, lấy ảnh QR.
- Resize ảnh về 128x128 pixels, và hiển thị lên một `MapView`, đưa vào tay người chơi.

#### `com.kaedee.napthe.database`
- Lớp `SQLiteManager` để tạo các bảng:
  - `users`: `uuid`, `name`, `ip_address`, `total_donated`, `current_crystal`.
  - `transactions`: `id`, `uuid`, `type` (CARD/QR), `amount_vnd`, `amount_crystal`, `status`, `timestamp`.

### 3. Workflow Thanh toán chi tiết

**Với SePay (QR):**
1. Player gõ `/napthe`, chọn QR, nhập `50k`.
2. Server gọi API tạo mã VietQR (`vietqr.io`) với nội dung là mã giao dịch và số tiền `50000`.
3. Server tạo một `MapView` ảo ép vào tay người chơi, yêu cầu dùng app ngân hàng quét.
4. Khi chuyển khoản, SePay bắn IPN POST về Server thông qua Cloudflare Tunnel.
5. Plugin xác nhận thông tin, cộng 5 Crystal (50k = 5), update `total_donated` += 50000. Uprank nếu đủ mốc.
6. Xóa Map QR trên tay người chơi.

**Với TheSieuRe (Thẻ Cào):**
1. Player chọn Thẻ cào, chọn Nhà Mạng, Mệnh giá (50k), Seri, Mã Thẻ.
2. Server gửi request nạp thẻ sang TheSieuRe.
3. TheSieuRe xử lý và bắn HTTP POST/GET callback về Web Server của plugin (thông qua Tunnel).
4. Nếu thành công: Cộng 4 Crystal (50k = 4, do tỉ lệ 0.8).
5. Nếu sai mệnh giá: Phạt 50% của mệnh giá thực (theo cấu hình).
6. Update tổng nạp và check Uprank.

## 🧪 Verification Plan

### Manual Verification
- Deploy plugin lên server, kết nối DB.
- Cấu hình Cloudflare Tunnel trỏ tới port 8080 của server.
- Lấy đường dẫn Tunnel nhập vào cấu hình IPN SePay và Callback TheSieuRe.
- Bắn request test từ Postman (giả lập SePay và TSR) để test chức năng cộng tiền.
- Vào game, test lệnh `/napthe`, xem render map QR code có chuẩn xác không.
- Chạy Vault API test đổi từ Crystal sang Koin.
