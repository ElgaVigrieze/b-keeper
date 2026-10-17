# 📒 B-Keeper

Bookkeeping application for small Latvian companies with a focus on EDS reporting automation. Handles invoices, payroll, VAT declarations.

[![Java](https://img.shields.io/badge/Java-25-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)](https://spring.io/projects/spring-boot)
[![SQLite](https://img.shields.io/badge/SQLite-3.47-blue)](https://www.sqlite.org/)
[![HTMX](https://img.shields.io/badge/HTMX-2.0-purple)](https://htmx.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

---

## Why This Exists

Small Latvian companies (SIA, IK) often struggle with bookkeeping:
- Enterprise software (1C, Horizon) is expensive and overcomplicated
- Spreadsheets don't generate the XML files required by VID (tax authority)
- Accountants charge €50-200/month for basic services

**B-Keeper** is a lightweight, offline-first solution that:
- Runs locally (no cloud subscription)
- Generates EDS-compatible XML files (PVN, DDZ)
- Handles Latvian tax specifics (VSAOI rates, non-taxable minimums, PVN annexes)
- Extracts invoice data from PDFs using OCR + local LLM

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| **Invoices (In/Out)** | Track incoming and outgoing invoices with VAT classification |
| **Payroll** | Calculate salaries with Latvian tax rates (VSAOI, IIN, solidarity tax) |
| **PVN Declaration** | Generate monthly VAT return with annexes (PVN 1-I, 1-II, 1-III) |
| **DDZ Report** | Generate employer's payroll report for EDS |
| **Fixed Assets** | Track equipment with depreciation calculations |
| **Journal** | Automatic income/expense journal entries |
| **Invoice OCR** | Extract data from PDF invoices using Tesseract + local LLM |
| **Google Drive Import** | Bulk-import outgoing invoices from a Google Drive folder (AI-extracted drafts) |
| **Offline-First** | SQLite database, works without internet |

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         B-KEEPER                                 │
│              Local-first bookkeeping application                 │
└─────────────────────────────────────────────────────────────────┘

┌──────────────┐     ┌──────────────┐     ┌──────────────────────┐
│   Browser    │────▶│  Spring Boot │────▶│   SQLite Database    │
│   (HTMX)     │◀────│  + Thymeleaf │◀────│   ~/bkeeper/bkeeper.db│
└──────────────┘     └──────┬───────┘     └──────────────────────┘
                            │
              ┌─────────────┼─────────────┐
              │             │             │
              ▼             ▼             ▼
       ┌──────────┐  ┌──────────┐  ┌──────────────┐
       │ Tesseract│  │  Ollama  │  │ EDS XML Gen  │
       │   OCR    │  │   LLM    │  │ (PVN, DDZ)   │
       └──────────┘  └──────────┘  └──────────────┘
```

### Tech Stack

| Layer | Technology | Why |
|-------|------------|-----|
| **Backend** | Spring Boot 4.1, Java 25 | Modern, reliable |
| **Database** | SQLite + Spring Data JDBC | Portable, no server needed |
| **Frontend** | Thymeleaf + HTMX + Bootstrap | Simple, no build step |
| **Migrations** | Flyway | Version-controlled schema |
| **PDF Parsing** | Apache PDFBox | Extract text from invoices |
| **OCR** | Tesseract + Ollama (mistral-nemo) | Handle scanned PDFs |

---

## 🚀 Quick Start

### Prerequisites
- Java 25
- Maven 3.8+
- (Optional) Tesseract OCR for scanned invoice extraction
- (Optional) Ollama with mistral-nemo for smart invoice parsing

### Run

```bash
cd b-keeper
mvn spring-boot:run
```

Open **http://localhost:8080**

Data is stored in `~/bkeeper/bkeeper.db` (SQLite file).

---

## 📊 Domain Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           LATVIAN BOOKKEEPING MODEL                          │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────┐       ┌───────────────┐       ┌───────────────┐
│ Company  │       │  Invoice Out  │──────▶│ Journal Entry │
│ (single) │       │ (izejošie)    │       │ (auto-created)│
└──────────┘       └───────────────┘       └───────────────┘
                          │                       ▲
                          ▼                       │
                   ┌─────────────┐         ┌─────────────┐
                   │  Customer   │         │ Invoice In  │
                   └─────────────┘         │ (ienākošie) │
                                           └──────┬──────┘
┌──────────┐       ┌───────────────┐              │
│ Employee │──────▶│ Payroll Entry │──────────────┤
└──────────┘       └───────────────┘              │
                          │                       ▼
                          ▼               ┌─────────────┐
                   ┌─────────────┐        │  Supplier   │
                   │EDS Submission│        └─────────────┘
                   │(PVN,DDZ,etc)│
                   └─────────────┘        ┌─────────────┐
                                          │ Fixed Asset │
                                          │(pamatlīdz.) │
                                          └─────────────┘
```

### Key Entities

| Entity | Purpose | Latvian Term |
|--------|---------|--------------|
| `InvoiceOut` | Sales invoices issued to clients | Izejošie rēķini |
| `InvoiceIn` | Purchase invoices from suppliers | Ienākošie rēķini |
| `PayrollEntry` | Monthly salary calculations | Algas aprēķins |
| `Employee` | Employee records with tax info | Darbinieki |
| `JournalEntry` | Income/expense ledger entries | Žurnāls |
| `FixedAsset` | Depreciable equipment (>€430) | Pamatlīdzekļi |
| `EdsSubmission` | Track submitted tax documents | EDS iesniegumi |

---

## 🇱🇻 Latvian Tax Logic

### Payroll Calculations

```java
// 2024 rates (configurable)
VSAOI_EMPLOYER  = 23.59%   // Employer social contributions
VSAOI_EMPLOYEE  = 10.50%   // Employee pension contributions
RISK_DUTY       =  0.46%   // Employer risk duty
IIN             = 20/23%   // Income tax (progressive)
```

### VAT (PVN) Line Codes

| Line | Description | Annex |
|------|-------------|-------|
| 41 | Domestic sales (standard rate) | PVN 1-III |
| 43 | Zero-rate supplies | PVN 1-III |
| 45 | EU supplies | PVN 1-III |
| 55 | EU goods purchases | PVN 1-II |
| 62 | Domestic purchases (input VAT) | PVN 1-I |

### EDS XML Generation

The app generates XML files compatible with [eds.vid.gov.lv](https://eds.vid.gov.lv):

```xml
<!-- PVN declaration example -->
<PVN1>
  <Galvene>
    <Maks_kods>41203041233</Maks_kods>
    <PVN_numurs>LV41203041233</PVN_numurs>
    ...
  </Galvene>
  <Dati>
    <R41><Summa>1000.00</Summa><PVN>210.00</PVN></R41>
    ...
  </Dati>
  <PVN1_I>...</PVN1_I>   <!-- Domestic purchases annex -->
  <PVN1_III>...</PVN1_III> <!-- Sales annex -->
</PVN1>
```

---

## 📁 Project Structure

```
b-keeper/
├── pom.xml
├── src/main/
│   ├── java/lv/bkeeper/
│   │   ├── BKeeperApplication.java
│   │   ├── config/
│   │   │   ├── DataSourceConfig.java    # SQLite setup
│   │   │   └── FlywayConfig.java        # DB migrations
│   │   ├── controller/
│   │   │   ├── DashboardController.java
│   │   │   ├── InvoiceInController.java
│   │   │   ├── InvoiceOutController.java
│   │   │   ├── PayrollController.java
│   │   │   ├── EdsController.java       # XML generation
│   │   │   └── ...
│   │   ├── domain/
│   │   │   ├── Company.java
│   │   │   ├── Employee.java
│   │   │   ├── InvoiceIn.java
│   │   │   ├── InvoiceOut.java
│   │   │   ├── PayrollEntry.java
│   │   │   └── ...
│   │   ├── repository/                  # Spring Data JDBC
│   │   └── service/
│   │       ├── EdsXmlService.java       # PVN/DDZ XML generation
│   │       ├── EdsReportService.java    # Tax calculations
│   │       ├── InvoiceOcrService.java   # PDF extraction
│   │       ├── GoogleDriveService.java  # Drive import (service account)
│   │       └── JournalService.java      # Auto journal entries
│   └── resources/
│       ├── application.properties
│       ├── db/migration/                # Flyway SQL scripts
│       │   ├── V1__initial_schema.sql
│       │   ├── V2__fix_epoch_dates.sql
│       │   └── ...
│       └── templates/                   # Thymeleaf + HTMX
│           ├── dashboard.html
│           ├── invoices/
│           ├── payroll/
│           ├── eds/
│           └── fragments/
```

---

## 🔧 Configuration

### application.properties

```properties
# Server
server.port=8080

# SQLite (stored in user home)
spring.datasource.url=jdbc:sqlite:${user.home}/bkeeper/bkeeper.db

# File uploads (for invoice PDFs)
spring.servlet.multipart.max-file-size=20MB
```

### Optional: Invoice OCR

For automatic invoice data extraction:

1. **Install Tesseract** (for scanned PDFs):
   - Windows: Download from [GitHub](https://github.com/UB-Mannheim/tesseract/wiki)
   - Add Latvian language pack (`lav`)

2. **Install Ollama** (for smart extraction):
   ```bash
   ollama pull mistral-nemo
   ```

### Optional: Google Drive import (outgoing invoices)

Import invoice PDFs straight from a Google Drive folder into outgoing invoices.
Files are downloaded, stored locally, read with AI extraction and saved as
**drafts** for review. Already-imported files are marked and skipped.

#### One-time setup

1. Open [console.cloud.google.com](https://console.cloud.google.com) and create a project (or pick an existing one).
2. **APIs & Services → Library** → enable **Google Drive API**.
3. **IAM & Admin → Service Accounts** → *Create service account* → open it →
   **Keys → Add key → Create new key → JSON** → download the key file.
4. Open B-Keeper → **Iestatījumi** (Settings):
   - paste the JSON key (or upload the file),
   - paste the link/ID of the Google Drive folder that contains the invoices,
   - press **Saglabāt**, then **Pārbaudīt savienojumu**.
5. In Google Drive, open that folder → **Share** → add the service account
   email (shown in Settings) with **Viewer** access.

#### Importing

**Izejošie rēķini → ⤓ Importēt no Google Drive** → select files (PDF, JPG, PNG,
WEBP, Google Docs or Google Sheets) → *Importēt atlasītos kā melnrakstus*.

Each file becomes a **DRAFT** invoice: client, dates and amounts are filled from
the AI extraction (where possible), the client is matched against the customer
database, and the original document is stored under
`~/bkeeper/documents/invoices-out/`. Review each draft and change its status to
*Izrakstīts*/*Apmaksāts* when it checks out.

> Without Ollama running, imports still work — drafts are created from the file
> name and modification date, and you fill in the rest manually.

---

## 🧪 Workflow Example

### Monthly Bookkeeping Cycle

1. **Enter incoming invoices** → `/invoices/in`
   - Upload PDF, OCR extracts data
   - Classify VAT line (62 domestic, 55 EU)

2. **Create outgoing invoices** → `/invoices/out`
   - Issue invoices to clients, or import them from Google Drive as drafts
   - Track payment status

3. **Run payroll** → `/payroll`
   - Calculate monthly salaries
   - Auto-compute VSAOI, IIN, net pay

4. **Generate EDS files** → `/eds`
   - Download PVN declaration XML
   - Download DDZ (payroll report) XML

5. **Submit to VID** → [eds.vid.gov.lv](https://eds.vid.gov.lv)
   - Upload XML files
   - Get confirmation number

---

## 🎯 Use Cases

- **Micro-companies** (1-5 employees) managing their own books
- **Freelancers** (IK) tracking income and expenses
- **Side businesses** with simple bookkeeping needs
- **Anyone** who wants to understand Latvian tax calculations

---

## 🔮 Future Enhancements

- [ ] Bank statement import (CSV/MT940)
- [ ] Automatic VAT rate detection by supplier country
- [ ] Dashboard charts (income/expense trends)
- [ ] PDF invoice generation (not just data entry)
- [ ] Multi-company support

---

## 📄 License

MIT License

---

## 👤 Author

**Elga Vigrieze**
- GitHub: [@ElgaVigrieze](https://github.com/ElgaVigrieze)
