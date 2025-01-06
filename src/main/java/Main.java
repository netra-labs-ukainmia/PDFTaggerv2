import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.*;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.itextpdf.kernel.pdf.canvas.*;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.ITextExtractionStrategy;
import java.io.*;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONArray;

public class Main {
    public static void main(String[] args) {
        try {
            // Define file paths
            String inputPdfPath = "src/main/resources/input/Sample Doc_Textract.pdf";
            String inputJsonPath = "src/main/resources/input/Sample Doc_Textract.json";
            String outputPath = "src/main/resources/output/Sample Doc_Textract_Tagged.pdf";

            System.out.println("Starting PDF processing...");

            // Check if files exist
            File pdfFile = new File(inputPdfPath);
            File jsonFile = new File(inputJsonPath);

            if (!pdfFile.exists()) {
                throw new FileNotFoundException("PDF file not found at: " + inputPdfPath);
            }
            if (!jsonFile.exists()) {
                throw new FileNotFoundException("JSON file not found at: " + inputJsonPath);
            }

            // Read JSON file
            FileReader reader = new FileReader(jsonFile);
            JSONObject jsonData = new JSONObject(new JSONTokener(reader));
            System.out.println("JSON file loaded successfully");

            // Create PDF document
            PdfDocument pdfDoc = new PdfDocument(
                new PdfReader(inputPdfPath),
                new PdfWriter(outputPath)
            );

            // Enable tagging
            pdfDoc.setTagged();

            // Get the first page
            PdfPage page = pdfDoc.getPage(1);
            float pageHeight = page.getPageSize().getHeight();

            // Create document structure element as root
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();
            TagTreePointer tagPointer = new TagTreePointer(pdfDoc);
            tagPointer.setPageForTagging(page);

            // Process JSON blocks
            System.out.println("Processing JSON blocks...");
            JSONArray blocks = jsonData.getJSONArray("Blocks");
            int blockCount = 0;

            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.getJSONObject(i);
                String blockType = block.getString("BlockType");

                // Skip PAGE blocks
                if (blockType.equals("PAGE")) {
                    continue;
                }

                // Get geometry
                JSONObject geometry = block.getJSONObject("Geometry");
                JSONObject boundingBox = geometry.getJSONObject("BoundingBox");

                // Get coordinates
                float left = (float) boundingBox.getDouble("Left");
                float top = (float) boundingBox.getDouble("Top");
                float width = (float) boundingBox.getDouble("Width");
                float height = (float) boundingBox.getDouble("Height");

                // Create rectangle for the block
                Rectangle rect = new Rectangle(
                    left * page.getPageSize().getWidth(),
                    (1 - top - height) * pageHeight,
                    width * page.getPageSize().getWidth(),
                    height * pageHeight
                );

                // Get text at this location
                String textAtLocation = block.optString("Text", "");

                // Determine role and create structure element
                String role = determineRole(blockType, block);
                if (role != null) {
                    // Set the appropriate role for the structure element
                    tagPointer.addTag(role);

                    // Create marked content
                    PdfDictionary properties = new PdfDictionary();
                    properties.put(PdfName.MCID, new PdfNumber(page.getNextMcid()));

                    // Begin marked content sequence
                    PdfCanvas canvas = new PdfCanvas(page);
                    canvas.beginMarkedContent(new PdfName(role), properties);

                    // Mark the region (optional, for debugging)
                    canvas.setLineWidth(0.1f);
                    canvas.rectangle(rect);
                    canvas.stroke();

                    // End marked content sequence
                    canvas.endMarkedContent();

                    // Move tag pointer back to root for next element
                    tagPointer.moveToParent();

                    blockCount++;
                }
            }

            System.out.println("Added tags to " + blockCount + " blocks");

            // Close everything
            reader.close();
            pdfDoc.close();

            System.out.println("Process completed. Tagged PDF saved to: " + outputPath);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String determineRole(String blockType, JSONObject block) {
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
                // Skip individual words as they're handled within LINE blocks
                return null;

            default:
                return "P";
        }
    }
}