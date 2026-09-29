package com.uptrail.sample;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiny synthetic PDF files for the sample claims. Every page says SAMPLE; none of them is a real receipt or
 * certificate.
 */
final class SampleDocuments {

    private SampleDocuments() {
    }

    static byte[] pdf(String heading, String... lines) {
        StringBuilder text = new StringBuilder("BT /F1 20 Tf 72 770 Td (").append(escape(heading)).append(") Tj ET\n");
        int y = 740;
        List<String> all = new ArrayList<>(List.of(lines));
        all.add("SAMPLE DOCUMENT - synthetic data for the Uptrail demo, not a real record.");
        for (String line : all) {
            text.append("BT /F1 11 Tf 72 ").append(y).append(" Td (").append(escape(line)).append(") Tj ET\n");
            y -= 18;
        }
        String stream = text.toString();
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R "
                        + "/Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length " + stream.getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n" + stream
                        + "endstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.length());
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            pdf.append(String.format("%010d 00000 n \n", offset));
        }
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replaceAll("[^\\x20-\\x7E]", "?");
    }
}
