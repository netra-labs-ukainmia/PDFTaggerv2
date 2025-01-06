import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.*;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.itextpdf.kernel.pdf.canvas.*;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import java.io.*;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONArray;

public class Main {
    private static PdfStructElem currentTable = null;
    private static PdfStructElem currentTableRow = null;
    private static PdfStructElem currentList = null;
    private static int currentRowIndex = -1;

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

            // Extract and print all text from the page
            System.out.println("\nAll text on page:");
            System.out.println(PdfTextExtractor.getTextFromPage(page));
            System.out.println("-------------------\n");

            // Process JSON blocks
            System.out.println("Processing JSON blocks...");
            JSONArray blocks = jsonData.getJSONArray("Blocks");
            int blockCount = 0;

            // Create document structure element as root
            PdfStructElem documentRoot = new PdfStructElem(pdfDoc, PdfName.Document);
            pdfDoc.getStructTreeRoot().addKid(documentRoot);

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

                // Debug output
                System.out.println("\nProcessing Block #" + i);
                System.out.println("Type: " + blockType);
                System.out.println("Coordinates: " + rect.toString());
                if (block.has("Text")) {
                    System.out.println("Text: " + block.getString("Text"));
                }
                System.out.println("-------------------");

                // Create properties for marked content
                PdfDictionary properties = new PdfDictionary();
                properties.put(PdfName.MCID, new PdfNumber(page.getNextMcid()));

                // Process block based on type
                PdfStructElem element = null;
                PdfName role = null;

                switch (blockType) {
                    case "LAYOUT_SECTION_HEADER":
                        int level = determineHeaderLevel(block);
                        role = new PdfName("H" + level);
                        element = new PdfStructElem(pdfDoc, role);
                        documentRoot.addKid(element);
                        break;

                    case "TABLE":
                        currentTable = new PdfStructElem(pdfDoc, PdfName.Table);
                        element = currentTable;
                        documentRoot.addKid(element);
                        currentRowIndex = -1;
                        break;

                    case "CELL":
                        if (currentTable == null) {
                            currentTable = new PdfStructElem(pdfDoc, PdfName.Table);
                            documentRoot.addKid(currentTable);
                        }

                        int rowIndex = block.optInt("RowIndex", -1);
                        if (rowIndex != currentRowIndex) {
                            currentTableRow = new PdfStructElem(pdfDoc, PdfName.TR);
                            currentTable.addKid(currentTableRow);
                            currentRowIndex = rowIndex;
                        }

                        element = new PdfStructElem(pdfDoc, PdfName.TD);
                        currentTableRow.addKid(element);
                        role = PdfName.TD;
                        break;

                    case "LAYOUT_LIST":
                        currentList = new PdfStructElem(pdfDoc, PdfName.L);
                        element = currentList;
                        documentRoot.addKid(element);
                        role = PdfName.L;
                        break;

                    case "LINE":
                        if (currentList != null && isListItem(block)) {
                            PdfStructElem li = new PdfStructElem(pdfDoc, PdfName.LI);
                            currentList.addKid(li);
                            element = new PdfStructElem(pdfDoc, new PdfName("LBody"));
                            li.addKid(element);
                            role = new PdfName("LBody");
                        } else {
                            role = PdfName.P;
                            element = new PdfStructElem(pdfDoc, role);
                            documentRoot.addKid(element);
                        }
                        break;

                    case "LAYOUT_FIGURE":
                        role = PdfName.Figure;
                        element = new PdfStructElem(pdfDoc, role);
                        documentRoot.addKid(element);
                        break;

                    case "WORD":
                        // Skip individual words as they're handled within LINE blocks
                        continue;

                    default:
                        role = PdfName.P;
                        element = new PdfStructElem(pdfDoc, role);
                        documentRoot.addKid(element);
                        break;
                }

                if (element != null && role != null) {
                    // Begin marked content
                    canvas.beginMarkedContent(role, properties);

                    // Try to get text at these coordinates
                    String textAtLocation = getTextAtLocation(page, rect);
                    if (textAtLocation != null && !textAtLocation.trim().isEmpty()) {
                        System.out.println("Found text at location: " + textAtLocation);
                    }

                    // Create MCR and add to element
                    PdfMcrDictionary mcr = new PdfMcrDictionary(page, element);
                    element.addKid(mcr);

                    // End marked content
                    canvas.endMarkedContent();

                    blockCount++;
                }

                // Reset structure tracking when appropriate
                if (blockType.equals("TABLE")) {
                    currentTable = null;
                    currentTableRow = null;
                    currentRowIndex = -1;
                } else if (blockType.equals("LAYOUT_LIST")) {
                    currentList = null;
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

    private static String getTextAtLocation(PdfPage page, Rectangle rect) {
        try {
            LocationTextExtractionStrategy strategy = new LocationTextExtractionStrategy();
            PdfCanvasProcessor processor = new PdfCanvasProcessor(strategy);
            processor.processPageContent(page);
            return strategy.getResultantText();
        } catch (Exception e) {
            System.err.println("Error extracting text: " + e.getMessage());
            return null;
        }
    }

    private static int determineHeaderLevel(JSONObject block) {
        try {
            String text = block.getString("Text").trim().toUpperCase();
            if (text.contains("HEADER 2")) return 2;
            if (text.contains("HEADER 3")) return 3;
            return 1; // Default to H1
        } catch (Exception e) {
            return 1; // Default to H1 if there's any error
        }
    }

    private static boolean isListItem(JSONObject block) {
        try {
            String text = block.getString("Text").trim();
            return text.matches("^[\\-•\\*]\\s.*|^\\d+\\.\\s.*");
        } catch (Exception e) {
            return false;
        }
    }
}