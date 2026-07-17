import sys
import fitz  # PyMuPDF

def extract_text(pdf_path):
    try:
        doc = fitz.open(pdf_path)
        text = ""
        for page in doc:
            text += page.get_text()
        return text
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python pdf_parser.py <pdf_path>", file=sys.stderr)
        sys.exit(1)
    
    pdf_text = extract_text(sys.argv[1])
    print(pdf_text)
