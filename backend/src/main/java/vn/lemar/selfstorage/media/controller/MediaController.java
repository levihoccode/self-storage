package vn.lemar.selfstorage.media.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.lemar.selfstorage.ApiEnvelope;
import vn.lemar.selfstorage.media.FileStorage;
import vn.lemar.selfstorage.media.application.MediaService;

/** Upload file dùng chung cho mọi flow — FE upload trước, submit URL vào flow sau. */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Operation(summary = "Upload file",
            description = "Nhận ảnh (JPEG/PNG/WebP) hoặc PDF, tối đa 10MB. Trả URL để dùng lại "
                    + "trong các flow (ảnh hiện trạng, scan hợp đồng, ảnh giấy tờ).")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Upload thành công",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {"message":"Tải file lên thành công",
                                     "data":{"url":"https://res.cloudinary.com/demo/anh.png",
                                             "publicId":"anh","bytes":20480,"format":"png"}}"""))),
            @ApiResponse(responseCode = "400",
                    description = "Thiếu file / file rỗng / sai định dạng / quá 10MB"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    })
    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiEnvelope<FileStorage.StoredFile> upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        return ApiEnvelope.ok("Tải file lên thành công",
                mediaService.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType()));
    }
}
