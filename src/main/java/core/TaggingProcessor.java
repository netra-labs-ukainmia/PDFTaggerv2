
package core;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.*;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.itextpdf.kernel.pdf.canvas.*;
import com.itextpdf.kernel.geom.Rectangle;
import org.json.JSONObject;
import org.json.JSONArray;

public class TaggingProcessor {
    private PdfDocument pdfDoc;
    private PdfPage page;
    private TagTreePointer tagPointer;
    private float pageHeight;

    public TaggingProcessor(PdfDocument pdfDoc) {
        this.pdfDoc = pdfDoc;
        this.pdfDoc.setTagged();
        this.page = pdfDoc.getPage(1);
        this.pageHeight = page.getPageSize().getHeight();
        this.tagPointer = new TagTreePointer(pdfDoc);
        initializeDocumentStructure();
    }

    private void initializeDocumentStructure() {
        tagPointer.addTag("Document");
    }

    public int processBlocks(JSONArray blocks) {
        int blockCount = 0;
        
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.getJSONObject(i);
            String blockType = block.getString("BlockType");

            if (blockType.equals("PAGE")) {
                continue;
            }

            if (processBlock(block)) {
                blockCount++;
            }
        }

        return blockCount;
    }

    private boolean processBlock(JSONObject block) {
        JSONObject geometry = block.getJSONObject("Geometry");
        JSONObject boundingBox = geometry.getJSONObject("BoundingBox");

        float left = (float) boundingBox.getDouble("Left");
        float top = (float) boundingBox.getDouble("Top");
        float width = (float) boundingBox.getDouble("Width");
        float height = (float) boundingBox.getDouble("Height");

        Rectangle rect = new Rectangle(
            left * page.getPageSize().getWidth(),
            (1 - top - height) * pageHeight,
            width * page.getPageSize().getWidth(),
            height * pageHeight
        );

        String role = determineRole(block.getString("BlockType"), block);
        if (role != null) {
            tagPointer.setPageForTagging(page);
            tagPointer.addTag(role);

            PdfDictionary properties = new PdfDictionary();
            int mcid = page.getNextMcid();
            properties.put(PdfName.MCID, new PdfNumber(mcid));

            PdfCanvas canvas = new PdfCanvas(page);
            canvas.beginMarkedContent(PdfName.Span, properties);
            canvas.setLineWidth(0.1f);
            canvas.rectangle(rect);
            canvas.stroke();
            canvas.endMarkedContent();

            tagPointer.moveToRoot();
            return true;
        }
        return false;
    }

    private String determineRole(String blockType, JSONObject block) {
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
                return "TD";

            case "LAYOUT_LIST":
                return "L";

            case "LAYOUT_FIGURE":
                return "Figure";

            case "WORD":
                return null;

            default:
                return "P";
        }
    }
}
