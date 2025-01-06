package core;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.*;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.itextpdf.kernel.pdf.tagutils.TagReference;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import org.json.JSONObject;
import java.util.*;
import java.io.IOException;

public class TaggingProcessor {
    private final PdfDocument pdfDoc;
    private final TagTreePointer pointer;
    private PdfPage currentPage;
    private final Map<String, String> roleMap;

    public TaggingProcessor(PdfDocument pdfDoc) {
        this.pdfDoc = pdfDoc;
        this.pdfDoc.setTagged();
        this.pointer = new TagTreePointer(pdfDoc);
        this.roleMap = initializeRoleMap();
        setupNamespace();
    }

    private Map<String, String> initializeRoleMap() {
        Map<String, String> map = new HashMap<>();

        // Document-level Tags
        map.put("DOCUMENT", "Document");
        map.put("DOCUMENTFRAGMENT", "DocumentFragment");
        map.put("PART", "Part");
        map.put("ART", "Art");
        map.put("SECT", "Sect");
        map.put("DIV", "Div");

        // Block-level Tags
        map.put("P", "P");
        map.put("H1", "H1");
        map.put("H2", "H2");
        map.put("H3", "H3");
        map.put("H4", "H4");
        map.put("H5", "H5");
        map.put("H6", "H6");
        map.put("BLOCKQUOTE", "BlockQuote");
        map.put("CAPTION", "Caption");
        map.put("INDEX", "Index");
        map.put("TOC", "TOC");
        map.put("TOCI", "TOCI");

        // List Structures
        map.put("L", "L");
        map.put("LI", "LI");
        map.put("LBL", "Lbl");
        map.put("LBODY", "LBody");

        // Table Structures
        map.put("TABLE", "Table");
        map.put("TR", "TR");
        map.put("TH", "TH");
        map.put("TD", "TD");
        map.put("THEAD", "THead");
        map.put("TBODY", "TBody");
        map.put("TFOOT", "TFoot");

        // Special Text Elements
        map.put("QUOTE", "Quote");
        map.put("NOTE", "Note");
        map.put("REFERENCE", "Reference");
        map.put("BIBENTRY", "BibEntry");
        map.put("CODE", "Code");
        map.put("FORMULA", "Formula");

        // Inline-level Tags
        map.put("SPAN", "Span");
        map.put("EM", "Em");
        map.put("STRONG", "Strong");
        map.put("SUB", "Sub");
        map.put("SUP", "Sup");

        // Ruby Annotations
        map.put("RUBY", "Ruby");
        map.put("RB", "RB");
        map.put("RT", "RT");
        map.put("RP", "RP");

        // Links and References
        map.put("LINK", "Link");
        map.put("ANNOT", "Annot");

        // Media and Figure Tags
        map.put("FIGURE", "Figure");
        map.put("FORM", "Form");
        map.put("CHART", "Chart");
        map.put("DIAGRAM", "Diagram");

        // Media Elements
        map.put("SOUND", "Sound");
        map.put("MOVIE", "Movie");

        // Special Purpose Tags
        map.put("ARTIFACT", "Artifact");
        map.put("BACKGROUND", "Background");
        map.put("HEADER", "Header");
        map.put("FOOTER", "Footer");
        map.put("PAGENUM", "PageNum");
        map.put("WATERMARK", "Watermark");

        // Programming Elements
        map.put("PROGRAMLISTING", "ProgramListing");
        map.put("SAMPLECODE", "SampleCode");
        map.put("CODELINE", "CodeLine");

        return map;
    }

    private void setupNamespace() {
        PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();

        // Add standard PDF 2.0 namespace
        PdfNamespace namespace = new PdfNamespace("http://iso.org/pdf/ssn");
        root.addNamespace(namespace);

        // Add MathML namespace if needed
        PdfNamespace mathNamespace = new PdfNamespace("http://www.w3.org/1998/Math/MathML");
        root.addNamespace(mathNamespace);
    }

    public void processBlock(JSONObject block, PdfPage page) throws IOException {
        this.currentPage = page;
        pointer.setPageForTagging(page);

        String blockType = block.getString("BlockType");
        String role = determineRole(blockType, block);

        if (role != null) {
            // Create structure element with proper role
            pointer.addTag(role);

            // Get text content and geometry
            String textContent = block.optString("Text", "");
            JSONObject boundingBox = block.getJSONObject("Geometry")
                                       .getJSONObject("BoundingBox");

            // Create marked content
            PdfCanvas canvas = new PdfCanvas(page);
            int mcid = page.getNextMcid();

            // Create properties dictionary with attributes
            PdfDictionary properties = createProperties(block, mcid);

            // Begin marked content
            canvas.beginMarkedContent(PdfName.Span, properties);

            // Set structure element attributes using the tag reference
            TagReference tagRef = pointer.getTagReference();
            canvas.openTag(tagRef);

            // Add the actual text content
            if (textContent != null && !textContent.isEmpty()) {
                canvas.setTextRise(0)
                      .beginText()
                      .setFontAndSize(getDefaultFont(), 12)
                      .moveText(0, 0)
                      .showText(textContent)
                      .endText();
            }

            // Mark the region
            markRegion(canvas, boundingBox, page);

            // Close the tag
            canvas.closeTag();

            // End marked content
            canvas.endMarkedContent();

            // Move pointer back to root for next element
            pointer.moveToRoot();
        }
    }

    private PdfFont getDefaultFont() throws IOException {
        return PdfFontFactory.createFont();
    }

    private PdfDictionary createProperties(JSONObject block, int mcid) {
        PdfDictionary properties = new PdfDictionary();
        properties.put(PdfName.MCID, new PdfNumber(mcid));

        // Add additional properties based on block type
        if (block.has("Lang")) {
            properties.put(PdfName.Lang, new PdfString(block.getString("Lang")));
        }

        return properties;
    }

    private void markRegion(PdfCanvas canvas, JSONObject boundingBox, PdfPage page) {
        float left = (float) boundingBox.getDouble("Left") * page.getPageSize().getWidth();
        float top = (float) boundingBox.getDouble("Top") * page.getPageSize().getHeight();
        float width = (float) boundingBox.getDouble("Width") * page.getPageSize().getWidth();
        float height = (float) boundingBox.getDouble("Height") * page.getPageSize().getHeight();

        Rectangle rect = new Rectangle(
            left,
            page.getPageSize().getHeight() - top - height,
            width,
            height
        );

        canvas.rectangle(rect);
        canvas.stroke();
    }

    private String determineRole(String blockType, JSONObject block) {
        // First check if it's a standard block type
        switch (blockType) {
            case "LAYOUT_SECTION_HEADER":
                String text = block.optString("Text", "").toUpperCase();
                if (text.contains("HEADER 2")) return "H2";
                if (text.contains("HEADER 3")) return "H3";
                return "H1";

            case "LINE":
                return "P";

            case "TABLE":
                return "Table";

            case "CELL":
                return block.optBoolean("IsHeader", false) ? "TH" : "TD";

            case "LAYOUT_LIST":
                return "L";

            case "LAYOUT_FIGURE":
                return "Figure";

            case "WORD":
                return null;
        }

        // Then check the role map for custom mappings
        String mappedRole = roleMap.get(blockType.toUpperCase());
        if (mappedRole != null) {
            return mappedRole;
        }

        // Default fallback
        return "P";
    }
}