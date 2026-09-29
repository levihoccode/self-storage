## Việc gì

<!-- 1-2 câu. Issue liên quan: Closes #__ -->

## Căn cứ trong spec

<!-- specs/draft.md › §…  ·  specs/db-table-draft.md › …  — hoặc: spec-gap #__ đã được duyệt -->

## Đã đổi gì

<!-- Gạch đầu dòng, theo file hoặc theo khu vực. Không liệt kê file không liên quan. -->

## Đã chạy gì — kèm kết quả

<!-- Dán output thật. Không có output nghĩa là chưa chạy. -->

```text
$ cd backend && ./mvnw -B verify
...
```

## Chưa kiểm chứng

<!-- BẮT BUỘC. Ghi rõ cái gì chưa chạy và vì sao. Không được để trống — ghi "không có" nếu thật sự đã kiểm hết. -->

## Rủi ro & chỗ cần review kỹ

<!-- migration · quyền · tiền · concurrency · dữ liệu cũ. Nêu thẳng, đừng giấu. -->

## Checklist

- [ ] Spec chỗ nào không rõ → đã mở **Spec gap** và dừng đúng ở phần đó
- [ ] Không sửa test để cho pass
- [ ] Đổi schema → có migration, không dùng `ddl-auto`
- [ ] Thay đổi trạng thái / tiền / quyền sở hữu → có `AuditLog`
- [ ] Không commit `.env`, credential, token
- [ ] Không mở rộng scope ngoài issue
