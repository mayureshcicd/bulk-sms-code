package com.message.approval.controller;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.message.approval.domain.BulkMessageReport;
import com.message.approval.service.BulkMessageReportService;
import com.message.approval.service.BulkMessageReportService.DateRange;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class BulkMessageReportController {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a");
    private final BulkMessageReportService reportService;

    public BulkMessageReportController(BulkMessageReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/reports/bulk-messages")
    public String report(@RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        YearMonth selectedMonth = month == null ? YearMonth.now() : month;
        DateRange range = range(period, selectedDate, selectedMonth);
        int safeSize = List.of(5, 10, 20, 50).contains(size) ? size : 10;
        Page<BulkMessageReport> reportPage = reportService.find(range.from(), range.to(), username,
                PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "sentAt")));
        model.addAttribute("reports", reportPage.getContent());
        model.addAttribute("reportPage", reportPage);
        model.addAttribute("period", normalizedPeriod(period));
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("username", username == null ? "" : username.trim());
        return "bulk-message-report";
    }

    @GetMapping(value = "/reports/bulk-messages.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(defaultValue = "") String username) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        YearMonth selectedMonth = month == null ? YearMonth.now() : month;
        DateRange range = range(period, selectedDate, selectedMonth);
        List<BulkMessageReport> reports = reportService.findAll(range.from(), range.to(), username);
        byte[] bytes = createPdf(reports, normalizedPeriod(period), selectedDate, selectedMonth);
        String suffix = "monthly".equalsIgnoreCase(period) ? selectedMonth.toString() : selectedDate.toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bulk-message-report-" + suffix + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @PostMapping("/reports/bulk-messages/delete")
    public String deleteSelected(@RequestParam(required = false) List<Long> selectedIds,
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) YearMonth month,
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "10") int size,
            RedirectAttributes redirectAttributes) {
        int deleted = reportService.deleteSelected(selectedIds);
        if (deleted == 0) redirectAttributes.addFlashAttribute("error", "Select at least one report row to delete.");
        else redirectAttributes.addFlashAttribute("success", deleted + " report row(s) permanently deleted.");
        redirectAttributes.addAttribute("period", normalizedPeriod(period));
        if (date != null) redirectAttributes.addAttribute("date", date);
        if (month != null) redirectAttributes.addAttribute("month", month);
        if (username != null && !username.isBlank()) redirectAttributes.addAttribute("username", username.trim());
        redirectAttributes.addAttribute("size", List.of(5, 10, 20, 50).contains(size) ? size : 10);
        return "redirect:/reports/bulk-messages";
    }

    private byte[] createPdf(List<BulkMessageReport> reports, String period,
            LocalDate date, YearMonth month) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
            PdfWriter.getInstance(document, output);
            document.open();
            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            document.add(new Paragraph("Bulk Message " + capitalize(period) + " Report", titleFont));
            document.add(new Paragraph("Period: " + ("monthly".equals(period) ? month : date)
                    + "    Total records: " + reports.size()));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[] { 1.25f, 0.9f, 1.25f, 0.75f, 0.8f, 1.0f, 1.5f, 2.55f });
            table.setWidthPercentage(100);
            for (String heading : List.of("Date", "Time", "Mobile", "Message ID", "Duplicates", "User", "CSV File", "Selected Message")) {
                PdfPCell cell = new PdfPCell(new Phrase(heading, new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(new Color(33, 37, 41));
                cell.setPadding(5);
                table.addCell(cell);
            }
            for (BulkMessageReport report : reports) {
                addCell(table, DATE.format(report.getSentAt()));
                addCell(table, TIME.format(report.getSentAt()));
                addCell(table, report.getRecipientMobile());
                addCell(table, String.valueOf(report.getMessageId()));
                addCell(table, String.valueOf(report.getDuplicateMessageCount()));
                addCell(table, report.getUsername());
                addCell(table, report.getCsvFileName());
                addCell(table, report.getMessageTitle() + "\n" + report.getMessageContent());
            }
            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create PDF report", e);
        }
    }

    private void addCell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value, new Font(Font.HELVETICA, 8)));
        cell.setPadding(4);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        table.addCell(cell);
    }

    private DateRange range(String period, LocalDate date, YearMonth month) {
        return "monthly".equalsIgnoreCase(period) ? reportService.monthly(month) : reportService.daily(date);
    }

    private String normalizedPeriod(String period) {
        return "monthly".equalsIgnoreCase(period) ? "monthly" : "daily";
    }

    private String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
