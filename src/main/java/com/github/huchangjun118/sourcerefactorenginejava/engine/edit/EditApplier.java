package com.github.huchangjun118.sourcerefactorenginejava.engine.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Applies a list of edits to a source string. */
public final class EditApplier {
    private EditApplier() {}

    /**
     * Apply edits to {@code source}. Edits must not overlap (conflict
     * resolution happens before this point). Applied back-to-front so
     * offsets stay valid; the result is deterministic for equal inputs.
     */
    public static String apply(String source, List<TextEdit> edits) {
        if (edits.isEmpty()) {
            return source;
        }
        List<TextEdit> sorted = new ArrayList<>(edits);
        Collections.sort(sorted);
        for (int i = 1; i < sorted.size(); i++) {
            TextEdit prev = sorted.get(i - 1);
            TextEdit cur = sorted.get(i);
            if (prev.getEnd() > cur.getStart()) {
                throw new IllegalStateException(
                        "overlapping edits reached EditApplier: " + prev.getRuleId()
                                + " vs " + cur.getRuleId() + " at offset " + cur.getStart());
            }
        }
        StringBuilder sb = new StringBuilder(source.length() + 64);
        int cursor = 0;
        for (TextEdit edit : sorted) {
            if (edit.getStart() < cursor) {
                throw new IllegalStateException("edit offsets out of order at " + edit.getStart());
            }
            sb.append(source, cursor, edit.getStart());
            sb.append(edit.getNewText());
            cursor = edit.getEnd();
        }
        sb.append(source, cursor, source.length());
        return sb.toString();
    }
}
