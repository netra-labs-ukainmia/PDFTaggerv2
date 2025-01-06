import com.itextpdf.kernel.pdf.*;
import core.TaggingProcessor;
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

            // Create TaggingProcessor
            TaggingProcessor processor = new TaggingProcessor(pdfDoc);

            // Get the first page
            PdfPage page = pdfDoc.getPage(1);

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

                // Process block using TaggingProcessor
                processor.processBlock(block, page);
                blockCount++;
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