package com.angus.aaps.ci.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

@Service
public class MarkdownService {
    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownService() {
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder()
            .escapeHtml(false)
            .build();
    }

    public String convertToHtml(String markdown) {
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }
}