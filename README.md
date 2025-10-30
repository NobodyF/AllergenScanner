# 🧠 Allergen Scanner – Android AI Application

**Allergen Scanner** is an Android-based mobile application that uses **computer vision** and **artificial intelligence** to automatically detect food allergens from product package images.

The app integrates a **custom YOLOv8 model (TensorFlow Lite)** with **on-device OCR (Google ML Kit)** and **multilingual text analysis** to find allergens in ingredient lists written in multiple languages.

---

## 🎯 Project Goal

The goal of this project is to demonstrate an end-to-end workflow combining machine learning, mobile development, and computer vision for a practical real-world purpose — helping users automatically identify allergens in food products.

This includes:
- Dataset creation and annotation using [MakeSense.AI](https://www.makesense.ai)
- Model training using YOLOv8
- TensorFlow Lite model integration in an Android app
- OCR text extraction and multi-language allergen detection
- Local result storage using Room Database

---

## ⚙️ System Overview

### 1️⃣ Image Input
The app currently allows users to select sample images from the **Assets** folder or from the **Gallery**.  
This setup is primarily for **testing**.  
In a real-world application, this can be **refactored** to capture photos using the **device camera** or to analyze images directly from the phone’s **photo library**.

### 2️⃣ Object Detection (YOLOv8 + TensorFlow Lite)
A custom-trained YOLOv8 model is used to detect **ingredient box regions** on product labels.  
Detected areas are cropped and analyzed for text recognition.

### 3️⃣ Text Extraction (OCR)
Detected regions are processed through **Google ML Kit** (on-device text recognition).  
The recognized text is normalized and cleaned for further analysis.

### 4️⃣ Allergen Detection
The system searches for allergen-related keywords across **multiple languages** (English, Latvian, Lithuanian, Russian, etc.).  
Supported allergen groups include:
- Milk, cheese, lactose, casein
- Eggs
- Fish, crustaceans, molluscs
- Peanuts and tree nuts
- Soy and lecithin (E322)
- Gluten, wheat, barley, rye, oats
- Sesame, celery, mustard, lupin, sulphites

Each detected allergen is displayed with:
- Severity (contains / may contain / trace / mentioned)
- Color-highlighted context in the text
- Descriptive explanation

### 5️⃣ Result Visualization
The results appear in a scrollable card:
- Summary list of detected allergens
- Details with explanations and context
- Highlighted OCR text for better readability

### 6️⃣ Local Storage (Room Database)
Every scan result (timestamp, allergens, OCR text) is stored locally in a Room database.  
A **History screen** allows users to review past scans.

---

## 🌍 Generalization

The app is capable of analyzing **any new or random product image** containing a readable ingredient list.  
If the YOLO detector fails to locate the ingredient area, the system automatically performs a **full-image OCR fallback** to ensure detection continues.

---

## 🧰 Technologies Used

| Category | Tools / Frameworks |
|-----------|--------------------|
| **AI Model** | YOLOv8 (Ultralytics) |
| **Model Conversion** | TensorFlow Lite |
| **OCR Engine** | Google ML Kit Text Recognition |
| **Language Coverage** | English, Latvian, Lithuanian, Russian |
| **Database** | Room (SQLite) |
| **UI Components** | ConstraintLayout, CardView, RecyclerView |
| **Language** | Kotlin |
| **Version Control** | Git + GitHub |

## 🧩 Features

- 📷 Detects ingredient boxes using YOLO
- 🔤 Extracts text via OCR (ML Kit)
- 🌐 Detects allergens across multiple languages
- 🎨 Highlights allergens visually
- 💾 Saves scan history locally
- ⚙️ Works fully offline (no network required)

---

## 🚀 Future Enhancements

- Integrate **live camera scanning** for real-time detection  
- Expand the allergen dictionary and language support  
- Improve model accuracy with larger datasets  
- Add cloud synchronization for scan history  
- Support export or sharing of allergen reports  

---

## 📸 Example Workflow

1. Select a test image from assets or gallery  
2. Tap **“Scan Allergens”**  
3. The app detects allergen keywords and highlights them  
4. View detected allergens and explanations  
5. Open **History** to see previously scanned results

---

## 🪪 License

This project is released under the **MIT License**.  
You are free to use, modify, and distribute it with proper attribution.
