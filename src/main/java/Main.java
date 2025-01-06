import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.*;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.itextpdf.kernel.pdf.canvas.*;
import com.itextpdf.kernel.geom.Rectangle;
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

            // Get the first page and create canvas
            PdfPage page = pdfDoc.getPage(1);
            PdfCanvas canvas = new PdfCanvas(page);
            float pageHeight = page.getPageSize().getHeight();

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

                // Create properties for marked content
                PdfDictionary properties = new PdfDictionary();
                properties.put(PdfName.MCID, new PdfNumber(page.getNextMcid()));

                // Create structure element and add marked content based on block type
                PdfName role = null;
                switch (blockType) {
                    case "LINE":
                        role = new PdfName(StandardRoles.P);
                        break;
                    case "TABLE":
                        role = new PdfName(StandardRoles.TABLE);
                        break;
                    case "CELL":
                        role = new PdfName(StandardRoles.TD);
                        break;
                    case "KEY_VALUE_SET":
                        role = new PdfName(StandardRoles.DIV);
                        break;
                    case "LAYOUT_SECTION_HEADER":
                        role = new PdfName(StandardRoles.H1);
                        break;
                    case "LAYOUT_TEXT":
                        role = new PdfName(StandardRoles.P);
                        break;
                    case "LAYOUT_FIGURE":
                        role = new PdfName(StandardRoles.FIGURE);
                        break;
                    case "LAYOUT_LIST":
                        role = new PdfName(StandardRoles.L);
                        break;
                    case "LAYOUT_TABLE":
                        role = new PdfName(StandardRoles.TABLE);
                        break;
                    case "WORD":
                        // Words are usually part of lines, so we skip them
                        continue;
                    default:
                        System.out.println("Unhandled block type: " + blockType);
                        continue;
                }

                if (role != null) {
                    // Create structure element
                    PdfStructElem element = new PdfStructElem(pdfDoc, role);
                    pdfDoc.getStructTreeRoot().addKid(element);

                    // Begin marked content
                    canvas.beginMarkedContent(role, properties);

                    // Create MCR and add to element
                    PdfMcrDictionary mcr = new PdfMcrDictionary(page, element);
                    element.addKid(mcr);

                    // End marked content
                    canvas.endMarkedContent();

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
}