# TÀI LIỆU ĐẶC TẢ SẢN PHẨM: CLIPPERCAST (v2, bản rà soát và bổ sung)

**Tên sản phẩm dự kiến:** ClipperCast – Clean Offline Reader & Article to Podcast
**Khẩu hiệu:** "Lưu mọi bài đọc từ Chrome — Đọc sạch không quảng cáo, nghe giọng đọc mượt mà offline"
**Định vị:** Bộ công cụ làm sạch trang web, lưu trữ bài viết ngoại tuyến và chuyển đổi văn bản thành podcast cá nhân không cần máy chủ.

> **Cách đọc tài liệu này**
> * **Phần I** là kết quả rà soát bản v1: lỗ hổng, mâu thuẫn và rủi ro, kèm đề xuất xử lý.
> * **Phần II** là đặc tả v2, giữ nguyên cấu trúc 5 mục của bản gốc và bổ sung thêm mục 6 đến 10.
> * Ký hiệu: **🆕 [BỔ SUNG]** là nội dung mới, **✏️ [SỬA]** là nội dung đã chỉnh so với v1, **⚠️** là rủi ro cần lưu ý. Nội dung không gắn ký hiệu được giữ nguyên từ v1.
> * Mức ưu tiên: **[MVP]** làm trong bản phát hành đầu, **[v1.1]**, **[v1.2]**, **[Sau]** làm ở các giai đoạn sau (xem mục 9).

---

# PHẦN I. KẾT QUẢ RÀ SOÁT BẢN v1

## I.1. Tóm tắt nhanh

Ý tưởng cốt lõi (Share từ Chrome → lưu offline → đọc sạch → nghe bằng TTS miễn phí, không backend) là **khả thi và có chi phí vận hành gần bằng 0**. Tuy nhiên bản v1 có 4 nhóm vấn đề lớn cần xử lý trước khi bắt tay vào code:

1. **Giả định kỹ thuật quá lạc quan:** Share Sheet chỉ trao cho app một **đường link**, không phải nội dung trang. App phải tự tải lại trang, nên các trang cần đăng nhập, có paywall, dựng bằng JavaScript hoặc chặn bot sẽ thất bại. Bản v1 chưa có phương án dự phòng nào.
2. **Cam kết marketing không giữ được:** "Loại bỏ 100% mã rác", "Google TTS trên 99% máy", "giọng Bắc/Nam", "3.0x không méo tiếng", "highlight từng chữ" đều chỉ đúng một phần và phụ thuộc thiết bị.
3. **Mâu thuẫn định vị:** Quảng bá "quyền riêng tư tuyệt đối, không tài khoản" nhưng lại đặt **banner quảng cáo** (SDK quảng cáo thu thập dữ liệu thiết bị) và có thể dùng **giọng TTS mạng** (gửi văn bản lên máy chủ Google).
4. **Thiếu các màn hình và luồng cơ bản:** onboarding, trạng thái rỗng, xử lý lỗi, trình phát mini, màn hình tìm kiếm, cài đặt, quản lý dung lượng, nhập dữ liệu từ dịch vụ khác, khả năng tiếp cận (accessibility), và chỉ số đo lường thành công.

## I.2. Bảng chi tiết các vấn đề

Mức độ: 🔴 Nghiêm trọng (phải sửa trước khi làm) · 🟠 Quan trọng (phải có lời giải trước khi phát hành) · 🟡 Nên cải thiện.

| # | Mục v1 | Vấn đề | Mức | Đề xuất xử lý (chi tiết ở Phần II) |
|---|---|---|---|---|
| 1 | 2.1 | Chrome chỉ chia sẻ URL (trong `EXTRA_TEXT`, đôi khi kèm tiêu đề). App tải lại trang **không có cookie đăng nhập của Chrome**, nên bài sau đăng nhập/paywall (Medium member-only, báo trả phí) sẽ chỉ lấy được phần mở đầu hoặc trang chặn. | 🔴 | Quy trình bóc tách 3 tầng: tải nhanh bằng HTTP → dựng lại bằng WebView ẩn → cho người dùng mở trang trong WebView của app để đăng nhập, hoặc dán văn bản thủ công. Mục 2.1. |
| 2 | 2.1 | Trang dựng bằng JavaScript (SPA), Reddit bản mới, trang có Cloudflare/chặn bot: JSoup chỉ thấy khung HTML rỗng. | 🔴 | WebView ẩn chạy JS rồi mới bóc tách; bộ chuyển đổi riêng cho một số trang (Reddit `.json`, Wikipedia REST API). Mục 2.1, 3. |
| 3 | 2.1 | "Xử lý ngầm trong 1 giây" không thực tế khi mạng chậm, ảnh nhiều. Snackbar không hiện được khi app không ở tiền cảnh. | 🟠 | Lưu link **ngay lập tức** (dưới 300 ms), bóc tách chạy nền bằng WorkManager. Dùng Toast để xác nhận "Đã lưu", và thông báo (notification) khi tải xong hoặc lỗi. Mục 2.1. |
| 4 | 2.1 | Chưa xử lý link đặc biệt: link AMP của Google, link Google News/Discover chuyển hướng, tham số theo dõi `utm_*`, link trùng, link PDF, link video, chia sẻ đoạn văn bản bôi đen thay vì link. | 🟠 | Chuẩn hóa URL, phát hiện trùng theo URL chuẩn (canonical), xử lý riêng từng loại nội dung. Mục 2.1. |
| 5 | 2.2 | "Loại bỏ 100% mã rác" không thể đảm bảo. Thuật toán Readability đôi khi cắt nhầm đoạn văn, bỏ sót bảng, mã nguồn, chú thích ảnh. | 🟠 | Đổi cam kết thành "loại bỏ phần lớn". Thêm nút "Xem bản gốc" và "Báo lỗi bóc tách" (lưu cục bộ, gửi qua email nếu người dùng muốn). Mục 2.2. |
| 6 | 3 | Viết lại (port) Mozilla Readability sang Kotlin tốn công và khó theo kịp bản gốc. | 🟡 | Dùng thư viện port có sẵn (ví dụ Readability4J) cho tầng nhanh, và chạy **chính Readability.js gốc** bên trong WebView cho tầng dự phòng. Mục 3. |
| 7 | 2.3 | "Google TTS cài sẵn trên 99% máy" không đúng: máy Huawei đời mới không có dịch vụ Google, nhiều máy Samsung mặc định dùng Samsung TTS, một số máy chưa tải dữ liệu giọng tiếng Việt. | 🟠 | Dùng bất kỳ engine TTS nào có trên máy, phát hiện thiếu giọng và hướng dẫn tải giọng offline. Mục 2.3. |
| 8 | 2.3 | "Giọng chuẩn Bắc/Nam" chưa được kiểm chứng: số giọng tiếng Việt của Google TTS ít và khác nhau theo phiên bản. Một số giọng là **giọng mạng**, cần Internet và gửi văn bản lên máy chủ. | 🟠 | Bỏ cam kết "Bắc/Nam" khỏi marketing cho đến khi kiểm thử thực tế. Ưu tiên giọng offline, đánh dấu rõ giọng nào cần mạng. Mục 2.3. |
| 9 | 2.3 | Highlight **từng chữ** dựa vào callback `onRangeStart` (Android 8+), nhưng không phải engine/giọng nào cũng hỗ trợ. | 🟠 | Highlight theo **câu** là mặc định (luôn chạy được). Highlight từng chữ chỉ bật khi engine hỗ trợ. Mục 2.3. |
| 10 | 2.3 | Tốc độ 3.0x "không méo tiếng" phụ thuộc engine; nhiều giọng ở trên 2.5x rất khó nghe. | 🟡 | Dải mặc định 0.5x–2.5x, bước 0.1x. Mức 2.6x–3.0x để trong "Thử nghiệm". Mục 2.3. |
| 11 | 2.3 | Gọi là "podcast" nhưng TTS phát trực tiếp **không có dòng thời gian** để tua theo giây như file âm thanh. | 🟡 | Thanh tiến trình theo câu/đoạn, tua ±1 câu hoặc ±1 đoạn, thời lượng ước tính. Giai đoạn sau: dùng `synthesizeToFile` để "Tải tập podcast" thành file âm thanh thật. Mục 2.3. |
| 12 | 3 | Phát âm thanh khi tắt màn hình cần **foreground service loại `mediaPlayback`**. Android 14+ bắt buộc khai báo loại dịch vụ, Google Play yêu cầu khai báo trong Play Console. | 🟠 | Dùng Media3 `MediaSessionService`, khai báo `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, xử lý audio focus, rút tai nghe thì dừng. Mục 3, 8. |
| 13 | 3 | Room chỉ hỗ trợ sẵn FTS3/FTS4, **không hỗ trợ sẵn FTS5**. Tìm tiếng Việt không dấu ("kinh te" ra "kinh tế") cần xử lý riêng. | 🟡 | Dùng `@Fts4` với tokenizer `unicode61` và thêm cột văn bản đã bỏ dấu. Mục 3. |
| 14 | 3 | Coil quản lý ảnh trong **bộ đệm (cache)** có thể bị xóa, không đảm bảo xem offline lâu dài. | 🟡 | Tự tải ảnh về thư mục riêng của từng bài trong `filesDir`, nén WebP, giới hạn chiều rộng. Coil chỉ dùng để hiển thị. Mục 3. |
| 15 | 4 | Vuốt trái vừa để "đưa vào danh sách phát" vừa để "xóa": **xung đột thao tác**. | 🟠 | Vuốt phải = Đã đọc/Lưu trữ, vuốt trái = Thêm vào hàng chờ nghe. Xóa qua nhấn giữ/menu, có "Hoàn tác". Mục 4. |
| 16 | 4 | Chạm giữa màn hình để hiện thanh công cụ dễ xung đột với nhấn giữ để bôi đen (highlight). | 🟡 | Quy định rõ cử chỉ trong trình đọc. Mục 4. |
| 17 | 4 | Thiếu: onboarding, trạng thái rỗng, trình phát mini, màn hình tìm kiếm, cài đặt, hàng chờ nghe, quản lý dung lượng, trạng thái tải từng bài. | 🟠 | Bổ sung đầy đủ trong mục 4 và 6. |
| 18 | 5.1 | **Banner quảng cáo mâu thuẫn với định vị quyền riêng tư và "Zen".** SDK quảng cáo thu thập mã quảng cáo/thông tin thiết bị, phải khai báo trong Data safety và hiện hộp thoại đồng ý (GDPR) ở EU. Doanh thu banner của app đọc tin quy mô nhỏ thường rất thấp. | 🔴 | Khuyến nghị **bỏ quảng cáo**, kiếm tiền hoàn toàn bằng gói Pro. Nếu vẫn muốn quảng cáo, phải bỏ khẩu hiệu "không theo dõi". Mục 5.1. |
| 19 | 5.1 | Giá $1.99/năm và $4.99 trọn đời: gói trọn đời chỉ bằng 2,5 năm, nên gói năm gần như vô nghĩa. Giá quá thấp cũng làm giảm cảm nhận giá trị. | 🟠 | Đề xuất bảng giá mới, có giá riêng cho Việt Nam. Mục 5.1. |
| 20 | 5.1 | Giới hạn "50 bài" chưa định nghĩa hành vi: bài thứ 51 chia sẻ vào thì sao? Nếu chặn lưu ngay tại Share Sheet sẽ phá vỡ trải nghiệm cốt lõi và mất link của người dùng. | 🔴 | Không bao giờ làm mất link. Giới hạn tính trên "bài chưa lưu trữ", có lối thoát rõ ràng. Mục 5.1. |
| 21 | 2.2 / 5.1 | Mâu thuẫn: mục 2.2 liệt kê 5 bảng màu và font OpenDyslexic như tính năng chung, mục 5.1 lại khóa "bảng màu đêm cao cấp" và "font độc quyền" cho VIP. Tương tự, mục 2.4 mô tả xuất file như tính năng chung, mục 5.1 khóa "xuất hàng loạt". | 🟠 | Bảng phân định Free/Pro rõ ràng. Font hỗ trợ chứng khó đọc và sao lưu/xuất dữ liệu cơ bản **luôn miễn phí**. Mục 5.1. |
| 22 | 2.2 | Font OpenDyslexic có thể **thiếu một số dấu tiếng Việt**. Font "độc quyền" cần giấy phép nhúng vào app. | 🟡 | Kiểm tra bảng ký tự tiếng Việt cho từng font trước khi đưa vào. Chỉ dùng font giấy phép SIL OFL/Apache. Mục 2.2. |
| 23 | 5.2 | **Pocket đã đóng cửa** (Mozilla dừng Pocket vào tháng 7/2025), Omnivore cũng đóng cửa cuối 2024. Từ khóa "Pocket alternative" vẫn có giá trị nhưng cần có tính năng **nhập dữ liệu** để thu hút người dùng cũ. Chèn tên thương hiệu khác vào tiêu đề/mô tả có thể vi phạm chính sách metadata của Google Play. | 🟠 | Thêm Nhập từ Pocket/Instapaper/Omnivore/bookmark HTML. Chỉ nhắc tên dịch vụ khác ở dạng mô tả tính năng ("Nhập dữ liệu từ Pocket"). Mục 2.4, 5.2. |
| 24 | 1, 5.2 | Thiếu phân tích đối thủ. Chính Google đã có "Nghe trang này" (Listen to this page) trong Chrome Android và app **Reading mode** có TTS. Ngoài ra còn @Voice Aloud Reader, Instapaper, Matter, Readwise Reader, ElevenReader, Speechify. | 🟠 | Thêm mục đối thủ và điểm khác biệt. Mục 1. |
| 25 | 5.2 | Mục tiêu 10 triệu lượt tải chưa có lộ trình, chưa có chỉ số đo, và **không có analytics** thì đo bằng gì? | 🟡 | Mục tiêu theo giai đoạn, đo bằng Play Console (không cần SDK). Mục 10. |
| 26 | 2.4 | Sao lưu thủ công dễ bị quên, mất máy là mất dữ liệu. | 🟡 | Tự động sao lưu định kỳ ra thư mục người dùng chọn (có thể là thư mục Google Drive/OneDrive qua bộ chọn file của Android) mà vẫn không cần backend. Mục 2.4. |
| 27 | Toàn bộ | Thiếu: xử lý lỗi, khả năng tiếp cận (TalkBack), chính sách lưu giữ dữ liệu, báo lỗi crash không dùng analytics, chính sách quyền riêng tư (Play vẫn bắt buộc có), quyền hệ thống cần xin. | 🟠 | Mục 6, 7, 8. |
| 28 | 2.2 | Rủi ro bảo mật: hiển thị HTML lấy từ trang web trong WebView có thể chạy mã độc nếu không làm sạch. | 🟠 | Làm sạch HTML bằng danh sách cho phép (JSoup Safelist), tắt JS của nội dung trang, chặn tải tài nguyên từ mạng trong trình đọc. Mục 3. |
| 29 | 2.3 | TTS đọc nguyên văn cả chú thích ảnh, khối mã nguồn, URL dài, bảng biểu: nghe rất khó chịu. Bài trộn tiếng Anh/tiếng Việt cần chọn đúng giọng. | 🟡 | Tiền xử lý văn bản trước khi đọc, tự nhận diện ngôn ngữ. Mục 2.3. |
| 30 | 1 | Câu "Tận dụng engine Google TTS ... không tốn một đồng" đúng về chi phí, nhưng thông điệp "AI tự nhiên" dễ bị so sánh với ElevenReader/Speechify (giọng AI đám mây chất lượng cao hơn rõ rệt). | 🟡 | Định vị bằng "miễn phí, offline, riêng tư" thay vì "giọng AI tự nhiên nhất". |

---

# PHẦN II. ĐẶC TẢ SẢN PHẨM v2

## 1. Bối cảnh thị trường & Khoảng trống của Hệ sinh thái Google

* **Hành vi người dùng Google:** Người dùng tìm kiếm trên Google hàng ngày, đọc báo, blog công nghệ, bài nghiên cứu trên Google Chrome và thường mở hàng chục tab để dành ("Read it later").
* **Điểm gây ức chế của Chrome di động:**
  * Bài viết ngập tràn quảng cáo nhấp nháy, pop-up xin cookies, banner che nửa màn hình, video tự động chạy làm hao pin và ngốn dữ liệu.
  * Mở 50-100 tab khiến Chrome bị giật lag, tải lại trang liên tục và gây tốn RAM.
  * Người dùng lưu nhưng không có thời gian ngồi đọc từng chữ vì bận lái xe, tập gym, nấu ăn.
* **Giải pháp của ClipperCast:** Tận dụng trực tiếp nút **"Share"** mặc định của Chrome: Bấm chia sẻ sang ClipperCast, trang web được bóc tách chỉ còn chữ và ảnh chính, lưu vào máy để đọc offline hoặc bấm 1 nút để nghe toàn bộ bài báo như một tập podcast bằng giọng đọc của máy. ✏️ [SỬA: bỏ "lập tức" và "giọng AI", xem rà soát #3, #30]

### 🆕 [BỔ SUNG] 1.1. Đối thủ và điểm khác biệt

| Đối thủ | Điểm mạnh | Điểm yếu mà ClipperCast khai thác |
|---|---|---|
| Chrome "Nghe trang này" và "Danh sách đọc" | Có sẵn, không cần cài | Cần mạng, không có thư viện offline thật sự, không có hàng chờ nghe nhiều bài liên tục |
| App Google "Reading mode" | Miễn phí, của Google, có TTS | Đọc trang đang mở, không phải thư viện lưu trữ lâu dài |
| @Voice Aloud Reader | Lâu đời, nhiều tính năng TTS | Giao diện cũ, khó dùng, nhiều quảng cáo |
| Instapaper | Thương hiệu "đọc sau" lâu năm | Bắt buộc tài khoản, dữ liệu trên máy chủ, TTS là tính năng trả phí |
| Matter, Readwise Reader | Trải nghiệm đẹp, giọng AI tốt | Đắt (thuê bao hàng tháng), cần tài khoản, ưu tiên iOS |
| ElevenReader, Speechify | Giọng AI đám mây rất tự nhiên | Cần mạng, giới hạn miễn phí, gửi nội dung lên máy chủ |
| Pocket, Omnivore | (Đã đóng cửa) | Người dùng cũ đang tìm nơi chuyển dữ liệu sang |

**Ba điểm khác biệt cốt lõi:** (1) **Offline-first thật sự:** bài và ảnh nằm trong máy, đọc và nghe khi không có mạng. (2) **Riêng tư thật sự:** không tài khoản, không máy chủ, không SDK theo dõi. (3) **Nghe miễn phí không giới hạn** nhờ TTS của máy, có hàng chờ nghe liên tục như podcast.

### 🆕 [BỔ SUNG] 1.2. Chân dung người dùng mục tiêu

* **Người đi làm bận rộn:** lưu bài trong ngày, nghe khi lái xe, đi xe buýt, tập gym.
* **Người học ngoại ngữ (IELTS, tiếng Anh chuyên ngành):** nghe kèm highlight để luyện nghe và phát âm.
* **Dân kỹ thuật, tài chính:** đọc nhiều nguồn nước ngoài, cần tìm kiếm lại và xuất ghi chú sang Obsidian/Notion.
* **Người có khó khăn khi đọc** (chứng khó đọc, thị lực kém, mỏi mắt): cần font, cỡ chữ, màu nền tùy chỉnh và nghe thay đọc.

---

## 2. Toàn bộ danh mục tính năng chi tiết

### 2.1. Đón đầu luồng chia sẻ từ Google Chrome (1-Tap Web Clipping)

* **Bắt link tự động qua Android Share Sheet:** [MVP]
  * Người dùng đang ở Chrome chỉ cần bấm: `Menu 3 chấm -> Chia sẻ -> ClipperCast`.
  * ✏️ [SỬA] App **lưu link ngay lập tức** (mục tiêu dưới 300 ms), đóng màn hình chia sẻ trong suốt và hiện Toast *"Đã lưu vào ClipperCast"*. Việc tải và bóc tách nội dung chạy nền bằng WorkManager, không làm gián đoạn việc lướt web. (Ghi chú: Snackbar chỉ hiện được khi app đang mở, nên tại thời điểm chia sẻ phải dùng Toast.)
  * 🆕 Khi bóc tách xong, hiện thông báo nhỏ với các nút: **Đọc ngay · Nghe ngay · Thêm nhãn**. Người dùng có thể tắt loại thông báo này trong Cài đặt.
  * 🆕 Nếu bóc tách thất bại, thông báo nói rõ lý do và cách xử lý (xem 2.1.2).
* 🆕 [BỔ SUNG] **Hai lựa chọn trong Share Sheet** (Android Sharing Shortcuts): [v1.1]
  * **"ClipperCast · Lưu"**: lưu để đọc sau.
  * **"ClipperCast · Nghe ngay"**: lưu và thêm vào đầu hàng chờ nghe, phát luôn.
* **Hỗ trợ xử lý nội dung đa nền tảng:**
  * Nhận diện và cào nội dung từ hầu hết các trang tin tức lớn, Medium, Substack, Reddit, Wikipedia, blog cá nhân.
  * 🆕 Nhận link từ **bất kỳ app nào** chia sẻ văn bản có chứa URL (Facebook, Zalo, Telegram, Gmail, Feedly...), không chỉ Chrome. [MVP]

#### 🆕 [BỔ SUNG] 2.1.1. Chuẩn hóa link trước khi lưu [MVP]

* Tách URL từ văn bản được chia sẻ (Chrome thường gửi kèm tiêu đề, một số app gửi cả đoạn văn có link ở giữa).
* Lần theo chuyển hướng: link rút gọn (bit.ly...), link AMP của Google (`google.com/amp/...`), link Google News/Discover.
* Bỏ tham số theo dõi: `utm_*`, `fbclid`, `gclid`...
* Lấy URL chuẩn từ thẻ `<link rel="canonical">` để **phát hiện bài trùng**. Nếu đã có, hiện Toast "Bài này đã có trong thư viện" và đưa bài cũ lên đầu danh sách.
* Nếu người dùng **chia sẻ đoạn văn bản bôi đen không có link**: lưu thành một "Ghi chú văn bản" có thể đọc và nghe như bài viết bình thường.

#### 🆕 [BỔ SUNG] 2.1.2. Quy trình bóc tách 3 tầng và phương án dự phòng [MVP]

| Tầng | Cách làm | Dùng khi |
|---|---|---|
| **1. Tải nhanh** | OkHttp tải HTML (giả lập trình duyệt di động) → JSoup + Readability chạy trên máy | Mặc định cho mọi link. Nhanh, tốn ít pin. |
| **2. Dựng lại bằng WebView ẩn** | WebView chạy JavaScript của trang trong tối đa ~15 giây → chạy Readability.js gốc ngay trong WebView → lấy kết quả | Tầng 1 cho ra nội dung quá ngắn (ví dụ dưới 200 từ), trang rỗng, hoặc trang thuộc danh sách "cần JS". |
| **3. Người dùng hỗ trợ** | (a) **Mở trong trình duyệt của app** để người dùng đăng nhập/qua bước kiểm tra bot, rồi bấm "Lưu trang này"; (b) **Dán văn bản thủ công**; (c) Trong Chrome, bôi đen toàn bộ bài và chia sẻ đoạn văn bản | Trang cần đăng nhập, có paywall, có captcha. |

* **Bộ chuyển đổi riêng cho trang phổ biến** [v1.1]: Reddit (dùng bản `.json` của bài viết để lấy bài và bình luận chính), Wikipedia (REST API), Substack, Medium. Danh sách này cập nhật theo bản phát hành app.
* **Phát hiện bài bị cắt do paywall:** nếu nội dung ngắn bất thường hoặc có dấu hiệu chặn ("Subscribe to continue reading"...), gắn nhãn "⚠️ Có thể chưa đầy đủ" trên thẻ bài và gợi ý tầng 3.
* **Link không phải bài viết:**
  * File PDF: [MVP] lưu link, mở bằng app xem PDF của máy. [Sau] bóc tách văn bản PDF để đọc và nghe.
  * Video (YouTube...), mạng xã hội dạng ngắn (X/Twitter, Threads), trang web ứng dụng: lưu dưới dạng "Chỉ có link" kèm tiêu đề và ảnh xem trước, không cố bóc tách.
* **Bài nhiều trang:** tự nhận diện link "Trang tiếp" và ghép nội dung (tối đa 10 trang). [v1.1]
* **Không có mạng lúc chia sẻ:** vẫn lưu link với trạng thái "Chờ tải", tự tải khi có mạng lại (WorkManager với điều kiện có mạng). Người dùng có thể chọn "Chỉ tải khi dùng Wi-Fi".

#### 🆕 [BỔ SUNG] 2.1.3. Trạng thái của một bài trong thư viện

`Chờ tải` → `Đang tải` → `Sẵn sàng` (đọc và nghe được offline) / `Có thể chưa đầy đủ` / `Lỗi` (kèm lý do) / `Chỉ có link`. Mỗi trạng thái có biểu tượng riêng trên thẻ bài và nút "Thử lại".

### 🆕 [BỔ SUNG] 2.1.4. Nhập dữ liệu từ dịch vụ khác [v1.1]

* **Pocket:** file CSV/HTML người dùng đã xuất trước khi Pocket đóng cửa (giữ nhãn, trạng thái đã đọc/lưu trữ, yêu thích).
* **Instapaper:** file CSV/HTML xuất từ cài đặt Instapaper.
* **Omnivore, Matter, Readwise:** file xuất (định dạng sẽ khảo sát khi làm).
* **Bookmark trình duyệt:** file HTML bookmark xuất từ Chrome/Firefox trên máy tính, chọn thư mục cần nhập.
* **Danh sách link dán tay:** dán nhiều URL, mỗi dòng một link.
* Nhập hàng loạt chạy nền, có thanh tiến trình và báo cáo cuối ("Đã nhập 1.240 bài, 37 bài lỗi, xem chi tiết"). Mặc định chỉ lưu link, tải nội dung dần dần khi có Wi-Fi để không tốn dữ liệu di động.
* ⚠️ Nhập dữ liệu là cửa vào quan trọng nhất với người dùng chuyển từ Pocket/Omnivore. Với gói miễn phí, bài nhập vào được đưa thẳng vào **Lưu trữ** để không chạm giới hạn (xem 5.1).

### 2.2. Trình đọc siêu sạch (Zen Reader Mode)

* **Thuật toán bóc tách bài viết trên thiết bị (Local Readability Engine):**
  * ✏️ [SỬA] Loại bỏ **phần lớn** nội dung rác: Banner quảng cáo, script theo dõi, pop-up đăng ký nhận email, chân trang rác, khung bình luận. (Không cam kết "100%", xem rà soát #5.)
  * Giữ lại nguyên vẹn: Tiêu đề, tác giả, ngày đăng, nội dung bài viết gốc và các hình ảnh minh họa.
  * ✏️ [SỬA] Ảnh được tải về máy, nén sang WebP và giới hạn chiều rộng (mặc định 1080 px) để cân bằng chất lượng và dung lượng. Có tùy chọn "Không tải ảnh" để tiết kiệm bộ nhớ.
  * 🆕 Giữ đúng định dạng: tiêu đề phụ, danh sách, trích dẫn, **bảng biểu**, **khối mã nguồn** (có cuộn ngang), chú thích ảnh, liên kết trong bài.
  * 🆕 Nội dung nhúng không bóc tách được (video, tweet, bản đồ) thay bằng ô giữ chỗ có link mở bản gốc.
  * 🆕 Nút **"Xem bản gốc"** (mở bằng Chrome) và **"Báo bóc tách sai"** trên mọi bài.
* **Trải nghiệm đọc chuẩn e-Reader:**
  * 5 bảng màu bảo vệ mắt: Trắng sáng, Giấy vàng cổ điển (Sepia), Xám dịu mắt, Tím than đêm, Đen OLED tuyệt đối. 🆕 Tất cả đạt độ tương phản tối thiểu 4.5:1 (chuẩn WCAG AA). 🆕 Tùy chọn "Theo giao diện hệ thống" tự đổi giữa một bảng sáng và một bảng tối.
  * Tùy chỉnh font chữ chuyên sâu: Serif (Merriweather, Lora), Sans-serif (Inter, Roboto), và font hỗ trợ người mắc chứng khó đọc (OpenDyslexic). ⚠️ Phải kiểm tra từng font hiển thị đủ dấu tiếng Việt; font thiếu dấu thì ẩn khi bài viết là tiếng Việt và hiện ghi chú.
  * Chỉnh kích thước chữ, khoảng cách dòng, độ thụt lề đầu dòng. 🆕 Thêm: độ rộng lề, căn đều hai bên, độ rộng tối đa của cột chữ (quan trọng trên máy tính bảng, điện thoại gập).
  * Ước tính thời gian đọc (ví dụ: *"Bài viết ~ 6 phút đọc"*). 🆕 Kèm thời gian nghe theo tốc độ đang chọn (*"~ 9 phút nghe ở 1.2x"*).
  * 🆕 Ghi nhớ vị trí đọc dở của từng bài, mở lại đúng chỗ. Thanh tiến độ mảnh ở đầu màn hình. [MVP]
  * 🆕 Chế độ **cuộn** (mặc định) hoặc **lật trang** như máy đọc sách. [v1.2]
  * 🆕 Tùy chọn giữ màn hình sáng khi đọc.

### 2.3. Chuyển văn bản thành Podcast Offline (Article-to-Podcast Engine)

* **Khai thác bộ tổng hợp giọng nói Android TTS (Text-to-Speech Engine):**
  * ✏️ [SỬA] Dùng **engine TTS có sẵn trên máy** (Google TTS, Samsung TTS hoặc engine khác người dùng cài). Với Google TTS, nhiều ngôn ngữ (trong đó có tiếng Việt) có giọng chạy offline, **không tốn chi phí token AI trên đám mây**.
  * ⚠️ Không cam kết "99% máy" và "giọng Bắc/Nam" cho đến khi kiểm thử trên các dòng máy phổ biến tại Việt Nam (Samsung, Xiaomi, OPPO, vivo, Realme). Danh sách giọng hiển thị trong app được **lấy trực tiếp từ engine**, không ghi cứng.
  * 🆕 **Kiểm tra giọng lần đầu:** nếu máy chưa có giọng offline cho ngôn ngữ của bài, hiện hướng dẫn và nút mở màn hình tải dữ liệu giọng của hệ thống.
  * 🆕 **Đánh dấu giọng cần mạng** (☁️). Trong chế độ "Riêng tư tối đa" (mặc định bật), app chỉ dùng giọng offline vì giọng mạng gửi văn bản lên máy chủ của nhà cung cấp engine.
  * 🆕 **Tự nhận diện ngôn ngữ** của bài (thuộc tính `lang` của trang, nếu thiếu thì nhận diện trên máy) để chọn giọng phù hợp. Người dùng chọn giọng ưa thích riêng cho từng ngôn ngữ.
  * 🆕 **Tiền xử lý văn bản trước khi đọc:** bỏ qua khối mã nguồn và URL dài (đọc "đường link"), tùy chọn đọc/bỏ chú thích ảnh, đọc tiêu đề phụ có ngắt nghỉ dài hơn, bảng biểu đọc "Bảng, 5 hàng, bỏ qua". Mở đầu mỗi bài bằng câu giới thiệu ngắn: "*[Tiêu đề], từ [Nguồn]*" (có thể tắt).
  * 🆕 **Từ điển phát âm cá nhân** [v1.2]: người dùng sửa cách đọc từ viết tắt, tên riêng (ví dụ "TP.HCM" đọc là "Thành phố Hồ Chí Minh").
* **Trình phát âm thanh chuyên nghiệp:**
  * ✏️ [SỬA] Điều chỉnh tốc độ đọc từ **0.5x đến 2.5x**, bước 0.1x. Mức 2.6x–3.0x để trong mục "Thử nghiệm" kèm ghi chú chất lượng phụ thuộc giọng đọc. (Xem rà soát #10.)
  * 🆕 Chỉnh **cao độ giọng** (pitch).
  * ✏️ [SỬA] Đánh dấu câu đang đọc: **highlight theo câu là mặc định** và luôn hoạt động. **Highlight theo từng chữ** chỉ bật khi engine/giọng hỗ trợ báo vị trí từ (callback `onRangeStart`, Android 8+). Màn hình tự cuộn theo câu đang đọc, có nút "Quay về vị trí đang đọc" khi người dùng cuộn đi chỗ khác. Hữu ích cho người học ngoại ngữ hoặc đọc sách chuyên ngành.
  * 🆕 **Chạm vào câu bất kỳ để nghe từ câu đó.**
  * 🆕 **Điều hướng kiểu podcast:** tua lùi/tới **1 câu** (chạm) hoặc **1 đoạn** (nhấn giữ), nhảy tới tiêu đề phụ. Thanh tiến trình theo câu/đoạn, hiển thị thời gian còn lại ước tính. (TTS phát trực tiếp không có dòng thời gian tính bằng giây như file âm thanh, xem rà soát #11.)
  * Tự động nối danh sách phát (Auto-queue): Tự động phát liên tục từ bài này sang bài khác như một playlist podcast trên xe ô tô hoặc xe buýt. 🆕 Có âm báo ngắn giữa hai bài. Sau khi nghe hết, tùy chọn tự đánh dấu "Đã đọc" và chuyển vào Lưu trữ.
  * 🆕 **Màn hình Hàng chờ nghe** (Up Next): kéo thả sắp xếp, xóa khỏi hàng chờ, "Phát tiếp theo", "Thêm vào cuối". [MVP]
  * Hỗ trợ nút tai nghe: Bấm nút trên tai nghe bluetooth để dừng/phát hoặc chuyển sang bài tiếp theo. 🆕 **Rút tai nghe hoặc ngắt Bluetooth thì tự dừng.** Tự giảm âm lượng/tạm dừng khi có cuộc gọi, chỉ đường của Google Maps hoặc app khác phát âm thanh (audio focus).
  * 🆕 **Hẹn giờ tắt** [MVP]: 5/15/30/45/60 phút, **"Hết bài này"**, hoặc tùy chỉnh. Giảm dần âm lượng 10 giây cuối. Lắc máy để gia hạn thêm 5 phút [v1.2].
  * 🆕 **Trình phát mini** luôn nằm ở đáy màn hình khi đang nghe, chạm để mở trình phát đầy đủ. [MVP]
  * 🆕 **Điều khiển ở màn hình khóa và thanh thông báo** với ảnh bìa bài, tiêu đề, nguồn. [MVP]
  * 🆕 **Android Auto** [v1.2]: duyệt Hàng chờ nghe, Mới lưu, Yêu thích và phát trên màn hình xe. ⚠️ App media cho Android Auto phải qua quy trình xét duyệt riêng của Google.
  * 🆕 **Tải tập podcast thành file âm thanh** [Sau]: dùng `synthesizeToFile` để tạo file (nén AAC/Opus) cho phép tua theo giây, nghe trên đồng hồ Wear OS hoặc chia sẻ cho chính mình. ⚠️ Tốn dung lượng (ước tính 0,5–1 MB/phút sau nén) và thời gian tạo file.

### 2.4. Quản lý thư viện & Ghi chú thông minh

* **Tổ chức thư mục & Nhãn dán (Tags):** Tự động phân loại theo tên miền (ví dụ: VnExpress, TechCrunch, Medium) hoặc tạo thẻ cá nhân (Công việc, Sức khỏe, Đầu tư).
  * 🆕 **Các ngăn chính:** Hộp thư (bài chưa đọc) · Đang đọc/nghe dở · Yêu thích · Lưu trữ. [MVP]
  * 🆕 **Sắp xếp:** mới lưu nhất, cũ nhất, ngắn nhất (đọc nhanh), dài nhất, theo nguồn. **Lọc:** theo nhãn, nguồn, ngôn ngữ, trạng thái, có/không có highlight.
  * 🆕 **Chọn nhiều bài** để gắn nhãn, lưu trữ, xóa, thêm vào hàng chờ cùng lúc.
* **Đánh dấu nổi bật (Highlighting) & Ghi chú (Notes):** [v1.1] Bôi đen đoạn văn tâm đắc để tô màu (vàng, xanh lá, hồng) và viết suy nghĩ cá nhân.
  * 🆕 **Màn hình "Tất cả highlight"** gom highlight của mọi bài, tìm kiếm và lọc theo màu/nhãn.
  * 🆕 Highlight được neo theo đoạn văn và nội dung trích dẫn, nên không bị lệch khi người dùng đổi cỡ chữ hoặc font.
  * 🆕 Chia sẻ một highlight ra ngoài dưới dạng văn bản trích dẫn kèm link nguồn (hoặc ảnh trích dẫn đẹp [v1.2]).
* 🆕 [BỔ SUNG] **Tìm kiếm toàn văn** [MVP]: tìm trong tiêu đề, nội dung, ghi chú, highlight; **gõ không dấu vẫn ra kết quả có dấu** ("kinh te" → "kinh tế"); hiện đoạn trích chứa từ khóa, bấm vào mở đúng vị trí trong bài. Lưu các lần tìm gần đây (chỉ trên máy, có thể xóa).
* **Xuất dữ liệu mở (Export Data):**
  * Xuất toàn bộ bài viết đã làm sạch sang định dạng file Markdown (`.md`), PDF hoặc Text thuần để lưu trữ vĩnh viễn trên máy tính hoặc ứng dụng Obsidian/Notion.
  * 🆕 File Markdown có phần thông tin đầu file (YAML front matter: tiêu đề, tác giả, URL, ngày lưu, nhãn) và highlight/ghi chú, tương thích Obsidian.
  * ✏️ [SỬA] Phân định rõ: **xuất từng bài miễn phí**, xuất hàng loạt thuộc gói Pro (xem 5.1).
  * 🆕 Ghi file qua bộ chọn file của Android (Storage Access Framework), **không cần xin quyền truy cập bộ nhớ**.
* **Sao lưu dữ liệu thủ công:** Xuất file nén JSON/ZIP chứa toàn bộ kho bài đọc để chuyển sang máy mới mà không cần đăng ký tài khoản đám mây.
  * 🆕 ✏️ [SỬA] Sao lưu và khôi phục **luôn miễn phí** (dữ liệu là của người dùng; khóa tính năng này sẽ làm mất niềm tin).
  * 🆕 **Sao lưu tự động định kỳ** [v1.1] (hằng ngày/hằng tuần) vào thư mục người dùng chọn, có thể là thư mục của Google Drive/OneDrive/Dropbox thông qua bộ chọn file Android. ClipperCast vẫn không cần máy chủ riêng. Giữ 5 bản gần nhất.
  * 🆕 Tùy chọn "Không kèm ảnh" để file sao lưu nhỏ hơn.
  * 🆕 File sao lưu có số phiên bản cấu trúc dữ liệu để bản app mới đọc được bản sao lưu cũ.
  * 🆕 Bật Android Auto Backup cho **cơ sở dữ liệu và cài đặt** (không gồm ảnh, vì giới hạn 25 MB của hệ thống), để người dùng đổi máy vẫn còn thư viện mà không phải làm gì.
* 🆕 [BỔ SUNG] **Đồng bộ nhiều thiết bị** [Sau]: đồng bộ qua thư mục đám mây của chính người dùng (không qua máy chủ ClipperCast). Chỉ làm khi có nhu cầu thực tế, vì xử lý xung đột dữ liệu phức tạp.

### 🆕 [BỔ SUNG] 2.5. Tiện ích màn hình chính, ô Cài đặt nhanh và lối tắt

* **Widget "Hàng chờ nghe"** [v1.1]: bài đang nghe, nút phát/dừng, bài tiếp theo, số bài chưa đọc.
* **Widget "Đọc nhanh"** [v1.2]: 3 bài ngắn nhất trong Hộp thư, chạm để mở.
* **Lối tắt khi nhấn giữ biểu tượng app** [MVP]: "Tiếp tục nghe", "Dán link từ bộ nhớ tạm", "Tìm kiếm".
* **Ô Cài đặt nhanh** (Quick Settings tile) [v1.2]: "Lưu link trong bộ nhớ tạm".
* **Lưu từ bộ nhớ tạm:** khi mở app, nếu bộ nhớ tạm có link chưa lưu, gợi ý "Lưu link vừa sao chép?". (Android 12+ sẽ hiện thông báo hệ thống khi app đọc bộ nhớ tạm, nên chỉ đọc khi người dùng bật tùy chọn này.)

### 🆕 [BỔ SUNG] 2.6. Thống kê đọc cá nhân [v1.2]

Chỉ tính và lưu trên máy: số bài đã đọc/nghe trong tuần, tổng thời gian nghe, chuỗi ngày đọc liên tiếp, nguồn đọc nhiều nhất. Không gửi đi đâu.

---

## 3. Kiến trúc kỹ thuật (100% Client-Side - 0 Backend)

* **Công nghệ cốt lõi:**
  * 🆕 Ngôn ngữ và giao diện: Kotlin, Jetpack Compose (Material 3). Android tối thiểu **8.0 (API 26)** để có `onRangeStart` và kênh thông báo.
  * ✏️ [SỬA] Phân tích cú pháp HTML: `JSoup` chạy trực tiếp trên Android, kết hợp **thư viện port sẵn của Mozilla Readability** (ví dụ Readability4J) cho tầng tải nhanh. Tầng dự phòng chạy **Readability.js gốc** trong WebView ẩn, nên luôn có thuật toán mới nhất mà không phải tự viết lại.
  * 🆕 Mạng: OkHttp (giới hạn thời gian chờ, giới hạn kích thước trang tối đa ~5 MB, theo chuyển hướng tối đa 10 lần).
  * 🆕 Tác vụ nền: WorkManager (tải bài, tải ảnh, nhập dữ liệu, sao lưu tự động), có điều kiện mạng/Wi-Fi và thử lại theo thời gian giãn dần.
  * ✏️ [SỬA] Cơ sở dữ liệu: Room Database với **FTS4** (Room chỉ hỗ trợ sẵn FTS3/FTS4), tokenizer `unicode61`, kèm một cột văn bản đã bỏ dấu tiếng Việt để tìm kiếm không dấu. Cho phép tìm nhanh trong hàng nghìn bài đã lưu.
  * ✏️ [SỬA] Trình phát âm thanh: Android TextToSpeech API bọc trong một Player tùy chỉnh của **Media3** (`SimpleBasePlayer`) và chạy trong `MediaSessionService` (về sau là `MediaLibraryService` cho Android Auto). Media3 tự lo thông báo điều khiển, màn hình khóa, nút tai nghe, audio focus.
    * Văn bản được chia theo câu trước khi đưa vào TTS (mỗi lần gửi có giới hạn độ dài, thường khoảng 4.000 ký tự), đồng thời giúp highlight theo câu và tua theo câu.
    * Đặt thuộc tính âm thanh là `USAGE_MEDIA` + `CONTENT_TYPE_SPEECH`.
    * Foreground service loại `mediaPlayback`, khai báo quyền `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (bắt buộc từ Android 14).
  * ✏️ [SỬA] Tải & Nén ảnh: tự tải ảnh bằng OkHttp, nén WebP, lưu vào `filesDir/articles/{id}/` (bộ nhớ trong riêng tư của app, không bị hệ thống tự dọn như cache). `Coil` chỉ dùng để **hiển thị** ảnh từ file cục bộ.
  * 🆕 Hiển thị bài đọc: WebView cục bộ hiển thị HTML **đã được làm sạch** bằng danh sách thẻ cho phép (JSoup Safelist): xóa mọi `<script>`, iframe, form, thuộc tính `on*`. Chặn mọi yêu cầu mạng từ trình đọc (ảnh chỉ tải từ file cục bộ), dùng Content-Security-Policy chặt. JavaScript chỉ dùng mã của chính app (highlight, cuộn theo câu đọc).
  * 🆕 WebView ẩn dùng cho bóc tách và trình duyệt trong app chạy trong **hồ sơ dữ liệu riêng**, có nút "Xóa dữ liệu đăng nhập trang web" trong Cài đặt.
  * 🆕 Thanh toán: Google Play Billing Library. Trạng thái Pro lưu cục bộ và đối chiếu lại với Google Play mỗi lần mở app, khôi phục mua hàng trên máy mới qua tài khoản Google Play (không cần tài khoản ClipperCast). ⚠️ Không có máy chủ xác minh nên có rủi ro bị bẻ khóa; chấp nhận được với mức giá thấp.
* **Không yêu cầu tài khoản người dùng:** Mở app lên dùng ngay lập tức, không có màn hình "Đăng nhập bằng Google/Facebook", tạo thiện cảm tuyệt đối về quyền riêng tư.

### 🆕 [BỔ SUNG] 3.1. Mô hình dữ liệu chính (tóm tắt)

* **Article:** id, url gốc, url chuẩn, tiêu đề, tác giả, nguồn (tên miền), ngày đăng, ngày lưu, ngôn ngữ, số từ, nội dung HTML sạch, nội dung văn bản thuần, trạng thái tải, trạng thái đọc (chưa đọc/đang đọc/đã đọc), lưu trữ, yêu thích, vị trí đọc, vị trí nghe (chỉ số câu), dung lượng trên máy.
* **Tag** và bảng nối **ArticleTag**.
* **Highlight:** id bài, chỉ số đoạn, vị trí bắt đầu/kết thúc, đoạn trích, màu, ghi chú, thời gian.
* **QueueItem:** id bài, thứ tự.
* **FailedClip:** url, lý do lỗi, số lần thử, lần thử gần nhất.

### 🆕 [BỔ SUNG] 3.2. Mục tiêu hiệu năng

| Chỉ số | Mục tiêu |
|---|---|
| Từ lúc bấm ClipperCast trong Share Sheet đến khi hiện Toast | < 300 ms |
| Bóc tách bài 2.000 từ ở tầng 1 trên máy tầm trung, mạng 4G | < 3 giây (trung vị) |
| Mở app (khởi động lạnh) | < 1,5 giây |
| Tìm kiếm trong 5.000 bài | < 200 ms |
| Từ lúc bấm Nghe đến khi có tiếng | < 1 giây |
| Tỉ lệ không crash (Android vitals) | > 99,5% người dùng |

---

## 4. Thiết kế giao diện (UI/UX)

* **Giao diện trang chủ:** Danh sách thẻ bài viết dạng tạp chí (Minimalist Magazine Style). Mỗi thẻ gồm ảnh thumbnail bo góc nhẹ, tên bài viết sắc nét, nguồn trang và thời lượng đọc/nghe.
  * 🆕 Thẻ bài có thêm: biểu tượng trạng thái tải (2.1.3), thanh tiến độ đọc/nghe dở, biểu tượng có highlight.
  * 🆕 Tùy chọn hiển thị: **Tạp chí** (ảnh lớn) / **Gọn** (danh sách dày, không ảnh) cho người lưu nhiều bài.
  * 🆕 Thanh trên cùng: ô tìm kiếm, nút lọc/sắp xếp, nút Cài đặt. Thanh dưới cùng: các ngăn Hộp thư · Hàng chờ nghe · Highlight · Lưu trữ.
  * 🆕 Nút **"+" nổi**: Dán link · Dán văn bản · Nhập từ dịch vụ khác.
  * 🆕 Kéo xuống để thử tải lại các bài đang lỗi/chờ.
* ✏️ [SỬA] **Thanh tác vụ nhanh (Quick Action Bar):**
  * Vuốt sang **phải**: đánh dấu "Đã đọc" và đưa vào Lưu trữ.
  * Vuốt sang **trái**: thêm vào Hàng chờ nghe.
  * **Xóa** không dùng thao tác vuốt (tránh xóa nhầm, xem rà soát #15): xóa qua nhấn giữ hoặc menu ba chấm. Mọi thao tác xóa/lưu trữ đều hiện Snackbar **"Hoàn tác"** trong 5 giây. Bài đã xóa vào **Thùng rác** 30 ngày trước khi xóa hẳn.
  * Người dùng có thể đổi hành động vuốt trong Cài đặt.
* ✏️ [SỬA] **Giao diện đọc sách:** Ẩn toàn bộ thanh điều hướng hệ thống (Immersive Mode).
  * **Chạm một lần** vào vùng giữa màn hình: hiện/ẩn thanh công cụ (font, bảng màu, nút Nghe, nhãn, chia sẻ, xem bản gốc).
  * **Nhấn giữ** vào chữ: chọn văn bản để highlight/ghi chú/sao chép/tra từ.
  * **Chạm vào một câu khi đang nghe:** nhảy đến câu đó.
  * Thanh công cụ tự ẩn khi cuộn xuống, hiện lại khi cuộn lên.
  * Bảng chỉnh hiển thị (font, cỡ chữ, màu...) mở dạng bảng trượt từ dưới lên, **xem trước trực tiếp** trên bài đang đọc.

### 🆕 [BỔ SUNG] 4.1. Danh sách màn hình

| Màn hình | Nội dung chính | Giai đoạn |
|---|---|---|
| **Onboarding** (3 bước, bỏ qua được) | (1) Giới thiệu "Chia sẻ từ Chrome → ClipperCast" bằng ảnh động; (2) **Thử ngay**: nút mở một bài mẫu trong Chrome để người dùng tự chia sẻ lần đầu, hoặc dùng bài mẫu có sẵn trong app; (3) Kiểm tra giọng đọc offline và xin quyền thông báo (Android 13+), giải thích lý do | MVP |
| **Trang chủ / Hộp thư** | Danh sách bài, lọc, sắp xếp, trạng thái | MVP |
| **Trình đọc** | Như mô tả ở trên | MVP |
| **Trình phát đầy đủ** | Ảnh bìa, tiêu đề, nguồn, câu đang đọc (dạng phụ đề lớn), tua câu/đoạn, tốc độ, hẹn giờ tắt, giọng đọc, nút mở Hàng chờ | MVP |
| **Trình phát mini** | Ở đáy mọi màn hình khi đang nghe | MVP |
| **Hàng chờ nghe** | Danh sách kéo thả, tổng thời lượng còn lại | MVP |
| **Tìm kiếm** | Ô tìm, tìm gần đây, kết quả có đoạn trích và từ khóa tô đậm | MVP |
| **Nhãn & nguồn** | Danh sách nhãn, số bài mỗi nhãn, đổi tên/gộp/xóa nhãn | MVP |
| **Highlight** | Tất cả highlight, lọc màu/nhãn | v1.1 |
| **Lưu trữ, Yêu thích, Thùng rác** | Danh sách tương tự Hộp thư | MVP |
| **Nhập dữ liệu** | Chọn nguồn, chọn file, tiến trình, báo cáo | v1.1 |
| **Cài đặt** | Xem 4.2 | MVP |
| **Nâng cấp Pro** | So sánh Free/Pro, giá, khôi phục mua hàng | MVP |
| **Trình duyệt trong app** | Để đăng nhập hoặc vượt kiểm tra bot rồi "Lưu trang này" | MVP |
| **Dán văn bản** | Ô nhập tiêu đề (không bắt buộc) và nội dung | MVP |

### 🆕 [BỔ SUNG] 4.2. Màn hình Cài đặt

* **Đọc:** bảng màu mặc định, font, cỡ chữ, khoảng cách dòng, theo giao diện hệ thống, giữ màn hình sáng, chế độ cuộn/lật trang.
* **Nghe:** giọng mặc định theo từng ngôn ngữ, tốc độ, cao độ, đọc câu giới thiệu, đọc chú thích ảnh, tự phát bài tiếp theo, tự lưu trữ sau khi nghe xong, chế độ "Riêng tư tối đa" (chỉ giọng offline), mở cài đặt TTS của hệ thống.
* **Lưu bài:** chỉ tải khi dùng Wi-Fi, tải ảnh (luôn/chỉ Wi-Fi/không bao giờ), chất lượng ảnh, thông báo khi lưu xong, tự gắn nhãn theo nguồn.
* **Thư viện:** hành động vuốt, kiểu hiển thị, sắp xếp mặc định.
* **Dung lượng & lưu giữ dữ liệu:** xem 7.
* **Sao lưu & khôi phục:** sao lưu ngay, sao lưu tự động, khôi phục, xuất dữ liệu, nhập dữ liệu.
* **Quyền riêng tư:** xóa dữ liệu đăng nhập của trình duyệt trong app, xóa lịch sử tìm kiếm, gửi báo cáo lỗi (xem 6.3).
* **Gói Pro:** trạng thái, khôi phục mua hàng.
* **Giới thiệu:** phiên bản, giấy phép mã nguồn mở và font, chính sách quyền riêng tư, liên hệ/góp ý, đánh giá trên Play Store.

### 🆕 [BỔ SUNG] 4.3. Trạng thái rỗng (empty states)

| Nơi | Nội dung hiển thị |
|---|---|
| Hộp thư trống (lần đầu) | Hình minh họa + "Chia sẻ bài viết từ Chrome để bắt đầu" + nút **Xem cách làm** (ảnh động 5 giây) + nút **Thử với bài mẫu** |
| Hộp thư trống (đã đọc hết) | "Bạn đã đọc hết! 🎉" + gợi ý nghe lại bài yêu thích hoặc mở Lưu trữ |
| Hàng chờ nghe trống | "Vuốt trái một bài để thêm vào hàng chờ" + nút "Thêm 5 bài mới nhất" |
| Tìm kiếm không có kết quả | "Không tìm thấy '...'" + gợi ý thử từ khác hoặc tìm cả trong Lưu trữ |
| Chưa có highlight | Hướng dẫn nhấn giữ để bôi đen trong trình đọc |
| Không có mạng | Dải thông báo nhỏ "Đang offline, các bài đã tải vẫn đọc và nghe được bình thường" |

### 🆕 [BỔ SUNG] 4.4. Khả năng tiếp cận (Accessibility) [MVP]

* Hỗ trợ đầy đủ **TalkBack**: mọi nút có mô tả; các thao tác vuốt có **hành động thay thế** trong menu của TalkBack (người dùng TalkBack không vuốt được kiểu thông thường).
* Tôn trọng cỡ chữ hệ thống ở giao diện app (ngoài trình đọc có cỡ chữ riêng). Kiểm tra bố cục ở cỡ chữ 200%.
* Vùng chạm tối thiểu 48×48 dp. Tương phản màu đạt WCAG AA ở mọi bảng màu.
* Không truyền tải thông tin chỉ bằng màu (trạng thái tải có cả biểu tượng và chữ).
* Tùy chọn giảm chuyển động (tắt hiệu ứng động khi hệ thống bật "Xóa hiệu ứng động").
* Highlight câu đang đọc phải phân biệt được với highlight do người dùng tô.

### 🆕 [BỔ SUNG] 4.5. Thích ứng nhiều kích thước màn hình [v1.2]

Máy tính bảng và điện thoại gập: bố cục hai cột (danh sách bên trái, bài đọc bên phải), giới hạn độ rộng cột chữ để dễ đọc.

### 🆕 [BỔ SUNG] 4.6. Ngôn ngữ giao diện

Tiếng Việt và tiếng Anh ngay từ MVP (định vị toàn cầu với từ khóa tiếng Anh ở mục 5.2). Hỗ trợ hiển thị bài viết ngôn ngữ viết từ phải sang trái (Ả Rập, Do Thái).

---

## 5. Chiến lược kiếm tiền & Tăng trưởng

### 5.1. Mô hình doanh thu

✏️ [SỬA] Toàn bộ mục này được viết lại. Lý do ở rà soát #18 đến #21.

* **Khuyến nghị: không đặt quảng cáo.** ClipperCast bán chính sự "sạch" và "riêng tư". Banner quảng cáo phá cả hai, đồng thời buộc app khai báo thu thập dữ liệu trong mục Data safety của Google Play và hiện hộp thoại đồng ý quảng cáo ở EU. Doanh thu banner của một app đọc tin quy mô nhỏ thường không đáng kể so với tổn thất về đánh giá và chuyển đổi lên gói trả phí.
  * *Phương án thay thế nếu vẫn muốn có quảng cáo:* chỉ đặt banner ở màn hình danh sách, tuyệt đối không trong trình đọc và trình phát, và **phải bỏ** các câu "không theo dõi", "quyền riêng tư tuyệt đối" khỏi mọi nội dung marketing. (Cần chủ sản phẩm quyết định.)

* **Bản miễn phí (đủ dùng thật sự):**
  * Lưu, đọc, nghe TTS không giới hạn thời gian sử dụng, hàng chờ nghe, hẹn giờ tắt, tìm kiếm.
  * ✏️ Giới hạn **50 bài đang hoạt động** (Hộp thư + đang đọc dở). **Bài đã đọc và chuyển vào Lưu trữ không tính vào giới hạn**, nên giới hạn khuyến khích đọc hết thay vì trừng phạt người dùng.
  * Cả 5 bảng màu, font hỗ trợ chứng khó đọc, xuất từng bài, sao lưu/khôi phục, nhập dữ liệu.
  * 5 nhãn tùy chỉnh, highlight không giới hạn số lượng nhưng chỉ màu vàng.
* **Hành vi khi chạm giới hạn 50 bài (không bao giờ làm mất link):**
  * Ở bài thứ 45: dải nhắc nhẹ trên Hộp thư "Bạn còn 5 chỗ trống".
  * Khi chia sẻ bài thứ 51: **link vẫn được lưu** nhưng nằm ở trạng thái "Chờ chỗ trống" (chưa tải nội dung). Thông báo có 3 lựa chọn: **Lưu trữ bài cũ nhất đã đọc dở** · **Mở thư viện để dọn** · **Nâng cấp Pro**.
  * Bài nhập từ dịch vụ khác vào thẳng Lưu trữ, không tính vào giới hạn.
* **Bản ClipperCast Pro** (✏️ đổi tên từ "VIP" cho thống nhất với thông lệ trên Play Store; có thể giữ "VIP" nếu muốn):
  * Không giới hạn số bài đang hoạt động.
  * Xuất hàng loạt PDF/Markdown, xuất highlight sang Obsidian/Notion theo lô.
  * Nhãn không giới hạn, highlight nhiều màu, ghi chú dài.
  * Sao lưu tự động định kỳ.
  * Bảng màu và font bổ sung (ngoài bộ 5 bảng màu và các font cơ bản đã miễn phí). Font phải có giấy phép cho phép nhúng vào app.
  * Từ điển phát âm cá nhân, widget nâng cao, thống kê đọc.
  * Android Auto và tạo file podcast (khi có).
* ✏️ **Đề xuất giá** (giá tham khảo, cần thử nghiệm A/B bằng tính năng thử nghiệm giá của Play Console):

| Gói | Quốc tế | Việt Nam | Ghi chú |
|---|---|---|---|
| Năm | $4.99 | 49.000 ₫ | Có 7 ngày dùng thử miễn phí |
| Trọn đời | $14.99 | 149.000 ₫ | Bằng khoảng 3 năm, hợp lý vì app không có chi phí máy chủ |

  * Lý do: với giá cũ ($1.99/năm và $4.99 trọn đời), gói trọn đời chỉ bằng 2,5 năm nên gần như ai cũng chọn trọn đời, và giá quá thấp làm người dùng nghi ngờ chất lượng. Google Play giữ lại 15% doanh thu (với nhà phát triển có doanh thu dưới 1 triệu USD/năm).
  * Vì app không có máy chủ, có thể cân nhắc **chỉ bán gói trọn đời** để thông điệp "trả một lần, dùng mãi, không thuê bao" trở thành điểm bán hàng.
* **Màn hình Nâng cấp:** bảng so sánh Free/Pro, không dùng chiêu trò (không đếm ngược giả, không hiện ngay khi mở app lần đầu). Chỉ hiện khi người dùng chạm vào tính năng Pro hoặc chạm giới hạn.

### 5.2. Đòn bẩy tăng trưởng

✏️ [SỬA] Đổi tiêu đề từ "Đòn bẩy đạt mốc 10 triệu lượt tải": 10 triệu là mục tiêu dài hạn, cần các mốc trung gian (xem mục 10).

* **ASO mạnh theo hành vi:** Tập trung vào các từ khóa mô tả nhu cầu: `Read later app`, `Offline reader`, `Article to audio`, `Web to speech`, `Text to speech reader`, `Chrome reader mode`, `Đọc báo offline`, `Nghe báo`.
  * ✏️ ⚠️ Với từ khóa thương hiệu khác (`Pocket alternative`, `Instapaper`): chỉ nhắc trong phần mô tả dưới dạng tính năng thật ("Nhập dữ liệu từ Pocket, Instapaper"), **không đưa vào tiêu đề app** để tránh vi phạm chính sách metadata của Google Play. Lưu ý Pocket đã đóng cửa năm 2025, nên lượng tìm kiếm sẽ giảm dần theo thời gian; nên tận dụng sớm.
  * 🆕 Ảnh chụp màn hình trên Play Store kể đúng 3 bước: Chia sẻ từ Chrome → Đọc sạch → Nghe như podcast. Video giới thiệu 15–30 giây.
* **Cộng đồng học thuật & năng suất:** Được giới thiệu mạnh mẽ trong các hội nhóm học tập, luyện IELTS, dân lập trình, giới tài chính cần đọc tin tức nước ngoài mỗi ngày.
  * 🆕 Đăng bài và hướng dẫn trên Reddit (r/androidapps, r/productivity, r/languagelearning), Tinh tế, Voz, các nhóm Facebook IELTS và Obsidian Việt Nam.
  * 🆕 Hướng dẫn chuyển nhà cho người dùng Pocket/Omnivore cũ ("Chuyển từ Pocket sang ClipperCast trong 2 phút").
* 🆕 **Vòng lặp tự lan tỏa (không cần backend):**
  * Chia sẻ highlight dạng ảnh trích dẫn có chữ nhỏ "Lưu bằng ClipperCast".
  * Hỏi đánh giá trên Play Store bằng In-App Review API vào **thời điểm tích cực** (ví dụ sau khi nghe hết bài thứ 10), không hỏi khi vừa gặp lỗi.
* 🆕 **Chương trình dùng thử trước khi phát hành:** kênh thử nghiệm kín/mở trên Play Console với 100–500 người dùng để thu thập lỗi bóc tách trên các trang báo Việt Nam phổ biến.

---

## 🆕 [BỔ SUNG] 6. Xử lý lỗi và báo cáo sự cố

### 6.1. Bảng lỗi và thông điệp

| Tình huống | Thông điệp cho người dùng | Hành động gợi ý |
|---|---|---|
| Không có mạng khi chia sẻ | "Đã lưu link. Sẽ tự tải khi có mạng." | (tự động) |
| Hết thời gian tải | "Trang phản hồi quá chậm." | Thử lại · Mở trong trình duyệt app |
| Trang cần đăng nhập/paywall | "Trang này cần đăng nhập để xem toàn bộ." | Mở để đăng nhập · Dán văn bản |
| Bị chặn bot/captcha | "Trang yêu cầu xác minh bạn không phải robot." | Mở trong trình duyệt app |
| Không bóc tách được nội dung | "Không tìm thấy nội dung bài viết trên trang này." | Xem bản gốc · Dán văn bản · Báo lỗi |
| Link không phải bài viết (video, ứng dụng web) | "Đã lưu dưới dạng link." | Mở bản gốc |
| Bộ nhớ máy gần đầy | "Bộ nhớ máy sắp đầy, đã tạm dừng tải ảnh." | Mở Quản lý dung lượng |
| Không có giọng đọc cho ngôn ngữ của bài | "Máy chưa có giọng đọc [ngôn ngữ]." | Tải giọng · Chọn giọng khác |
| Không có engine TTS nào | "Máy chưa có ứng dụng đọc văn bản." | Mở Play Store tới Google TTS |
| Khôi phục bản sao lưu lỗi | "File sao lưu bị hỏng hoặc không đúng định dạng." | Chọn file khác |
| Thanh toán lỗi/đang chờ | "Giao dịch đang chờ xử lý, Pro sẽ được mở khi hoàn tất." | (tự động kiểm tra lại) |

Nguyên tắc: thông điệp nói **chuyện gì xảy ra** và **làm gì tiếp theo**, không hiện mã lỗi kỹ thuật (mã lỗi để trong mục "Chi tiết").

### 6.2. Màn hình "Bài lỗi"

Gom tất cả bài lỗi/chờ ở một nơi, có nút "Thử lại tất cả", hiện lý do từng bài.

### 6.3. Báo cáo crash không dùng analytics

* **Android vitals trong Play Console** là nguồn chính: tự có báo cáo crash và ANR, không cần cài SDK nào.
* Thêm **ACRA** (thư viện mã nguồn mở) ở chế độ **hỏi trước khi gửi**: khi app bị crash, lần mở sau hỏi "Gửi báo cáo lỗi cho nhà phát triển?". Báo cáo chỉ gồm thông tin kỹ thuật (phiên bản app, dòng máy, phiên bản Android, nhật ký lỗi), **không gồm URL hay nội dung bài viết**, và được gửi qua app email của người dùng nên họ thấy rõ nội dung trước khi bấm gửi.
* Nút **"Báo bóc tách sai"** tạo email có sẵn URL bài (người dùng thấy và đồng ý trước khi gửi), giúp cải thiện bộ chuyển đổi cho các trang phổ biến.
* **Không dùng** Firebase Analytics, Crashlytics hay bất kỳ SDK theo dõi nào.

---

## 🆕 [BỔ SUNG] 7. Dung lượng và chính sách lưu giữ dữ liệu

* **Màn hình Quản lý dung lượng:** tổng dung lượng, chia theo nội dung bài/ảnh/bản sao lưu; danh sách bài chiếm nhiều dung lượng nhất.
* **Dọn dẹp:**
  * Xóa ảnh của bài đã lưu trữ (giữ lại chữ).
  * Tự xóa bài trong Lưu trữ sau 30/90/180 ngày hoặc không bao giờ (mặc định: không bao giờ). Bài Yêu thích và bài có highlight không bao giờ bị tự xóa.
  * Thùng rác tự xóa sau 30 ngày.
  * Giới hạn dung lượng tối đa cho ảnh (ví dụ 1 GB), vượt thì xóa ảnh của bài cũ nhất đã lưu trữ.
* **Gỡ app:** toàn bộ dữ liệu bị xóa theo hệ thống. Onboarding và Cài đặt nhắc người dùng bật sao lưu tự động.
* **Không có dữ liệu nào rời khỏi máy** trừ: (1) yêu cầu tải trang web và ảnh gửi tới chính trang đó; (2) giọng TTS mạng nếu người dùng tắt chế độ "Riêng tư tối đa"; (3) file sao lưu người dùng tự chọn nơi lưu; (4) email báo lỗi người dùng tự gửi; (5) giao dịch mua qua Google Play.

---

## 🆕 [BỔ SUNG] 8. Tuân thủ Google Play và quyền hệ thống

### 8.1. Quyền cần khai báo

| Quyền | Lý do |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Tải bài viết, kiểm tra Wi-Fi |
| `POST_NOTIFICATIONS` (Android 13+) | Thông báo lưu xong/lỗi, điều khiển phát. Xin ở bước onboarding, giải thích rõ; app vẫn chạy nếu bị từ chối |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Phát TTS khi tắt màn hình |
| `WAKE_LOCK` | Giữ phát âm thanh ổn định khi máy ngủ (Media3 quản lý) |
| Không cần | Quyền bộ nhớ (dùng bộ chọn file), vị trí, danh bạ, micro |

### 8.2. Yêu cầu của Google Play

* **Khai báo foreground service** loại `mediaPlayback` trong Play Console, kèm mô tả và video ngắn minh họa tính năng nghe bài viết.
* **Mục Data safety:** khai báo "Không thu thập dữ liệu" (đúng khi không có quảng cáo và không có analytics). Nếu thêm quảng cáo, phải khai báo lại.
* **Chính sách quyền riêng tư:** vẫn bắt buộc có một trang công khai, kể cả khi không thu thập dữ liệu. Có thể đặt trên GitHub Pages (không phải backend).
* **Chính sách metadata:** không dùng tên thương hiệu khác trong tiêu đề/biểu tượng; không cam kết sai sự thật ("100%", "99%").
* **Ngôn từ marketing:** mô tả là "chế độ đọc" (reader mode), **không** quảng bá là "trình chặn quảng cáo", vì Google Play hạn chế các app can thiệp vào quảng cáo của app/trang khác.
* **Bản quyền nội dung:** bài viết chỉ được lưu cho mục đích cá nhân trên máy người dùng. Không có tính năng chia sẻ công khai toàn bộ nội dung bài viết cho người khác (chỉ chia sẻ link hoặc trích đoạn highlight).
* **Android Auto** (khi làm): app phải qua xét duyệt chất lượng cho xe của Google.
* **API mục tiêu:** luôn theo mức target API tối thiểu Google Play yêu cầu hằng năm.

---

## 🆕 [BỔ SUNG] 9. Lộ trình phát hành

| Giai đoạn | Thời gian ước tính* | Phạm vi |
|---|---|---|
| **MVP (1.0)** | 10–12 tuần | Share Sheet + chuẩn hóa link + bóc tách 3 tầng; trình đọc (5 bảng màu, font, cỡ chữ, vị trí đọc); TTS với highlight theo câu, tốc độ, hàng chờ, hẹn giờ tắt, trình phát mini, màn hình khóa, nút tai nghe; Hộp thư/Lưu trữ/Yêu thích/Thùng rác; nhãn cơ bản; tìm kiếm không dấu; sao lưu/khôi phục thủ công; xuất từng bài Markdown; onboarding, trạng thái rỗng, xử lý lỗi; Cài đặt; quản lý dung lượng; Pro + Play Billing; tiếng Việt/tiếng Anh; TalkBack |
| **1.1** | +6 tuần | Highlight & ghi chú, màn hình Highlight; nhập từ Pocket/Instapaper/bookmark; sao lưu tự động; widget Hàng chờ nghe; hai lựa chọn trong Share Sheet; bộ chuyển đổi riêng cho Reddit/Wikipedia/Substack/Medium; bài nhiều trang; highlight từng chữ (khi engine hỗ trợ); xuất hàng loạt, xuất PDF |
| **1.2** | +6 tuần | Android Auto; từ điển phát âm; chế độ lật trang; bố cục máy tính bảng/gập; widget Đọc nhanh; ô Cài đặt nhanh; thống kê đọc; ảnh trích dẫn |
| **Sau** | Theo phản hồi | Tạo file podcast; đọc PDF; đồng bộ qua thư mục đám mây của người dùng; Wear OS; tóm tắt bài bằng AI chạy trên máy (chỉ trên các máy hỗ trợ, ví dụ Gemini Nano) |

\* Ước tính cho 1 lập trình viên Android có kinh nghiệm, chưa gồm thời gian thiết kế và kiểm thử với người dùng.

**Tiêu chí phát hành MVP:** bóc tách đúng ≥ 90% trên bộ kiểm thử 200 URL (50 trang báo Việt Nam, 100 trang tiếng Anh phổ biến, 50 blog/Medium/Substack); TTS chạy được trên 10 dòng máy phổ biến nhất tại Việt Nam; tỉ lệ không crash > 99,5% ở kênh thử nghiệm.

---

## 🆕 [BỔ SUNG] 10. Chỉ số thành công

Vì không có analytics, các chỉ số lấy từ **Google Play Console** (lượt cài, giữ chân, đánh giá, Android vitals, doanh thu) và **phản hồi người dùng**.

| Nhóm | Chỉ số | Mục tiêu 6 tháng sau MVP | Nguồn |
|---|---|---|---|
| Tăng trưởng | Lượt cài đặt | 50.000 | Play Console |
| Giữ chân | Tỉ lệ người cài còn giữ app sau 30 ngày | ≥ 35% | Play Console |
| Chất lượng | Điểm đánh giá trung bình | ≥ 4,5 sao | Play Console |
| Chất lượng | Tỉ lệ không crash / ANR | > 99,5% / < 0,47% | Android vitals |
| Bóc tách | Tỉ lệ bóc tách thành công trên bộ 200 URL (chạy lại mỗi bản phát hành) | ≥ 92% | Bộ kiểm thử nội bộ |
| Doanh thu | Tỉ lệ người dùng mua Pro | 2–4% | Play Console |
| Doanh thu | Tỉ lệ hoàn tiền | < 3% | Play Console |

**Mốc dài hạn:** 1 triệu lượt cài trong 18–24 tháng là mục tiêu tham vọng nhưng có cơ sở nếu giữ chân và đánh giá tốt. Mốc 10 triệu cần thêm thị trường quốc tế, bản địa hóa nhiều ngôn ngữ và nhiều khả năng cả bản iOS, nên chưa đặt làm mục tiêu cho năm đầu.

**Thống kê ẩn danh tùy chọn [Sau]:** nếu cần hiểu sâu hơn, có thể hỏi người dùng đồng ý gửi một bản tóm tắt ẩn danh (ví dụ: số bài lưu mỗi tuần, tỉ lệ dùng TTS) qua email theo quý. Mặc định tắt.

---

## 🆕 [BỔ SUNG] 11. Các câu hỏi chủ sản phẩm cần quyết định

1. **Có đặt quảng cáo hay không?** (Khuyến nghị: không. Xem 5.1.)
2. **Bán gói năm + trọn đời, hay chỉ trọn đời?** (Khuyến nghị: thử nghiệm A/B; nghiêng về chỉ trọn đời.)
3. **Tên "VIP" hay "Pro"?**
4. **MVP có cần tiếng Anh không,** hay tập trung thị trường Việt Nam trước?
5. **Giữ cam kết "giọng Bắc/Nam" không?** Cần kiểm thử thực tế các giọng tiếng Việt trên máy Samsung, Xiaomi, OPPO trước khi đưa vào marketing.
