import express from 'express';
import path from 'path';
import http from 'http';

const app = express();
const PORT = 3000;

// Middleware for parsing json and urlencoded data
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Serve static assets from bulkmessagecomposer/src/main/resources/static
const staticDir = path.join(process.cwd(), 'bulkmessagecomposer', 'src', 'main', 'resources', 'static');
app.use(express.static(staticDir));

// Fallback proxy / mock endpoints for local UI inspection and testing
app.get('/api/whatsapp/status', (_req, res) => {
  res.json({
    status: 'connected',
    connected: true,
    phone: 'Registered Device',
    pushName: 'Admin System'
  });
});

app.get('/api/whatsapp/account', (_req, res) => {
  res.json({
    mobileNumber: 'Configured & Active'
  });
});

app.get('/api/contacts', (_req, res) => {
  res.json([]);
});

app.get('/api/contacts/tags', (_req, res) => {
  res.json([]);
});

app.get('/api/approved-messages', (_req, res) => {
  res.json([
    {
      id: 1,
      name: 'Welcome Announcement',
      content: 'Hello! Thank you for connecting with us.',
      approvalStatus: 'APPROVED',
      files: []
    }
  ]);
});

// Proxy handler for send-bulk-plain-text
app.post('/api/whatsapp/send-bulk-plain-text', (req, res) => {
  const batchId = 'batch-plain-' + Date.now();
  res.json({
    success: true,
    batchId: batchId,
    status: 'processing',
    totalRecipients: req.body.phoneLimit || 1,
    message: req.body.message || 'Plain text message',
    delayMs: req.body.delayMs || 3000,
    randomizeDelay: false
  });
});

// Proxy handler for send-bulk-mms
app.post('/api/whatsapp/send-bulk-mms', (req, res) => {
  res.json({
    success: true,
    totalRecipients: 1,
    successCount: 1,
    failureCount: 0,
    documentName: 'media-attachment',
    caption: 'Media Attachment with Caption',
    details: [
      { phoneNumber: 'All recipients', status: 'success', messageId: 'mms-ok-' + Date.now() }
    ]
  });
});

// Proxy handler for send-bulk-text
app.post('/api/whatsapp/send-bulk-text', (req, res) => {
  const batchId = 'batch-wa-' + Date.now();
  res.json({
    success: true,
    batchId: batchId,
    status: 'processing',
    totalRecipients: req.body.phoneLimit || 1,
    message: req.body.message || 'WhatsApp message',
    delayMs: req.body.delayMs || 3000,
    randomizeDelay: false
  });
});

// Proxy handler for send-bulk-email
app.post('/api/whatsapp/send-bulk-email', (_req, res) => {
  res.json({
    success: true,
    status: 'completed',
    batchId: 'batch-email-' + Date.now(),
    totalRecipients: 1,
    sentMessages: 1,
    failedMessages: 0,
    message: 'Email dispatched successfully via configured SMTP'
  });
});

app.get('/api/whatsapp/batch-status/:id', (req, res) => {
  res.json({
    status: 'completed',
    sentMessages: 1,
    totalMessages: 1,
    failedMessages: 0,
    batchId: req.params.id
  });
});

// Default route sends index.html
app.use((_req, res) => {
  res.sendFile(path.join(staticDir, 'index.html'));
});

const server = http.createServer(app);
server.listen(PORT, '0.0.0.0', () => {
  console.log(`Server listening on http://0.0.0.0:${PORT}`);
});
