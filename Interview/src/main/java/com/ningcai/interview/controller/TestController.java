package com.ningcai.interview.controller;

import com.ningcai.interview.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class TestController {

    private final RagService ragService;

    @GetMapping("/load")
    public String load(@RequestParam String path) {
        return ragService.loadMaterials(path);
    }

    @GetMapping("/search")
    public String search(@RequestParam String q) {
        return ragService.search(q);
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String q) {
        return ragService.ask(q);
    }

    @GetMapping("/quick")
    public String quick(@RequestParam String path, @RequestParam String q) {
        StringBuilder result = new StringBuilder();
        result.append(ragService.loadMaterials(path)).append("\n\n");
        result.append(ragService.ask(q));
        return result.toString();
    }
}