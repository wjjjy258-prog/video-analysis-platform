package com.video.analysis.controller;

import com.video.analysis.dto.crawler.CrawlRunResponse;
import com.video.analysis.dto.crawler.CrawlUrlRequest;
import com.video.analysis.dto.crawler.ClearDataRequest;
import com.video.analysis.dto.crawler.TextImportRequest;
import com.video.analysis.service.CrawlerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/crawler")
public class CrawlerController {

    private final CrawlerService crawlerService;

    public CrawlerController(CrawlerService crawlerService) {
        this.crawlerService = crawlerService;
    }

    @PostMapping("/run-url")
    public CrawlRunResponse runUrlCrawl(@RequestBody @Valid CrawlUrlRequest request) {
        return crawlerService.runUrlCrawl(request.getPlatform(), request.getUrls(), request.isConfirmRisk());
    }

    @PostMapping("/run-mock")
    public CrawlRunResponse runMockCrawl() {
        return crawlerService.runMockCrawl();
    }

    @PostMapping("/clear-data")
    public CrawlRunResponse clearData(@RequestBody ClearDataRequest request) {
        return crawlerService.clearTenantData(request != null && request.isConfirm());
    }

    @GetMapping("/rejects")
    public List<Map<String, Object>> listRejects(@RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        return crawlerService.listImportRejects(limit);
    }

    @PostMapping("/import-text")
    public CrawlRunResponse importText(@RequestBody @Valid TextImportRequest request) {
        return crawlerService.importFromText(request.getText(), request.getDefaultPlatform(), request.isAiAssist());
    }

    @PostMapping(value = "/import-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CrawlRunResponse importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "defaultPlatform", defaultValue = "unknown") String defaultPlatform,
            @RequestParam(value = "aiAssist", defaultValue = "false") boolean aiAssist
    ) throws Exception {
        return crawlerService.importFromFile(file.getOriginalFilename(), file.getBytes(), defaultPlatform, aiAssist);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CrawlRunResponse> handleIllegalArgument(IllegalArgumentException ex) {
        CrawlRunResponse response = new CrawlRunResponse();
        response.setSuccess(false);
        response.setExitCode(1);
        response.setMessage(ex.getMessage());
        response.setOutput("");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CrawlRunResponse> handleOtherExceptions(Exception ex) {
        CrawlRunResponse response = new CrawlRunResponse();
        response.setSuccess(false);
        response.setExitCode(1);
        response.setMessage("执行失败: " + ex.getMessage());
        response.setOutput("");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CrawlRunResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("请求参数校验失败");
        CrawlRunResponse response = new CrawlRunResponse();
        response.setSuccess(false);
        response.setExitCode(1);
        response.setMessage(message);
        response.setOutput("");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
