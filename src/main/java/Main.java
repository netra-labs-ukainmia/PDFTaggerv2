import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.*;
import java.io.*;

public class Main {
    public static void main(String[] args) {
        try {
            // Define input and output paths using resource directory
            String inputPath = "src/main/resources/input/Sample Doc_Textract.pdf";
            String outputPath = "src/main/resources/output/Sample Doc_Textract_Tagged.pdf";

            // Create PDF document
            PdfDocument pdfDoc = new PdfDocument(
                new PdfReader(inputPath),
                new PdfWriter(outputPath)
            );

            // Enable tagging
            pdfDoc.setTagged();

            // Get the structure tree root
            PdfStructTreeRoot root = pdfDoc.getStructTreeRoot();

            // Process each page
            int numberOfPages = pdfDoc.getNumberOfPages();
            System.out.println("Processing " + numberOfPages + " pages...");

            // Close the document
            pdfDoc.close();

            System.out.println("PDF has been tagged and saved to: " + outputPath);

        } catch (Exception e) {
            System.err.println("Error processing PDF: " + e.getMessage());
            e.printStackTrace();
        }
    }
}