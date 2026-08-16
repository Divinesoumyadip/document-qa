package com.schooldesk.docqa.extraction;

public record ExtractedPage(int pageNumber, String text) {
    public boolean hasContent() {
        return text != null && !text.isBlank();
    }
}
