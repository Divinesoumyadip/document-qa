package com.schooldesk.docqa.extraction;

import java.util.List;

public record ExtractionResult(List<ExtractedPage> pages) {
    public boolean isEmpty() {
        return pages.stream().noneMatch(ExtractedPage::hasContent);
    }
}
