package com.xgsheng.backend.project;

import com.xgsheng.backend.exception.PendingProductRuleException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectRulesTest {

    @Test
    void nameMustBePresentWithinLengthAndWithoutControlCharacters() {
        assertEquals("深圳项目", ProjectRules.normalizeName("  深圳项目  "));
        assertEquals("project name is required",
                assertThrows(IllegalArgumentException.class, () -> ProjectRules.normalizeName(null)).getMessage());
        assertEquals("project name is required",
                assertThrows(IllegalArgumentException.class, () -> ProjectRules.normalizeName("   ")).getMessage());
        assertEquals("project name is too long",
                assertThrows(IllegalArgumentException.class,
                        () -> ProjectRules.normalizeName("项".repeat(101))).getMessage());
        assertEquals("project name is invalid",
                assertThrows(IllegalArgumentException.class, () -> ProjectRules.normalizeName("项目\n名称")).getMessage());
        assertEquals(100, ProjectRules.normalizeName("项".repeat(100)).codePointCount(0, 100));
    }

    @Test
    void watermarkPositionUsesTheFourConfirmedAnchors() {
        assertEquals("bottom_left", ProjectRules.normalizePosition(null, true));
        assertEquals("top_right", ProjectRules.normalizePosition("top_right", false));
        assertEquals("invalid watermark position",
                assertThrows(IllegalArgumentException.class,
                        () -> ProjectRules.normalizePosition("center", false)).getMessage());
    }

    @Test
    void pageUsesDefaultsAndDoesNotClampOutOfRangeValues() {
        ProjectPage defaults = ProjectPage.resolve(null, null);
        assertEquals(1, defaults.getPage());
        assertEquals(20, defaults.getPageSize());
        assertEquals(0L, defaults.offset());

        ProjectPage page = ProjectPage.resolve(2, 50);
        assertEquals(50L, page.offset());

        assertThrows(PendingProductRuleException.class, () -> ProjectPage.resolve(0, 20));
        assertThrows(PendingProductRuleException.class, () -> ProjectPage.resolve(1, 51));
        assertThrows(PendingProductRuleException.class, () -> ProjectPage.resolve(1, 0));
    }
}
