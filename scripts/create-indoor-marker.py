"""Rebuild the full-size A4 marker sheet. Requires reportlab.

The unmodified reference image comes from Google's ARCore Augmented Images sample.
Its licence is bundled alongside the app asset. Width here MUST match images.txt (0.18 m).
"""
from pathlib import Path
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm

root = Path(__file__).resolve().parents[1]
target = root / "output/pdf/GuideLight-indoor-marker.pdf"
target.parent.mkdir(parents=True, exist_ok=True)
pdf = canvas.Canvas(str(target), pagesize=A4)
pdf.setTitle("GuideLight indoor test - 18 cm Earth marker")
pdf.setAuthor("GuideLight")
pdf.setFillColorRGB(0.07, 0.14, 0.24)
pdf.setFont("Helvetica-Bold", 22)
pdf.drawString(15 * mm, 277 * mm, "GuideLight indoor test")
pdf.setFont("Helvetica", 11)
pdf.drawString(15 * mm, 267 * mm, "Print on A4 at Actual size / 100%. Turn off Fit to page.")
pdf.drawImage(str(root / "app/src/main/assets/indoor/home-marker.jpg"),
              15 * mm, 68 * mm, width=180 * mm, height=180 * mm)
pdf.setStrokeColorRGB(0.15, 0.32, 0.65)
pdf.line(15 * mm, 61 * mm, 195 * mm, 61 * mm)
for x in (15, 195):
    pdf.line(x * mm, 59 * mm, x * mm, 63 * mm)
pdf.setFont("Helvetica-Bold", 11)
pdf.drawCentredString(105 * mm, 54 * mm, "Picture width: exactly 18 cm (180 mm)")
pdf.setFont("Helvetica", 10)
for y, text in [
    (43, "Fix the sheet flat on a wall in good light. Do not move it after scanning."),
    (36, "Open GuideLight > Navigate > Test indoor setup > Start camera test."),
    (29, "Point at the whole Earth picture. Wait for the spoken 'Marker found'."),
    (22, "Tracking test only. Room navigation and obstacle warnings are not active here."),
]:
    pdf.drawString(15 * mm, y * mm, text)
pdf.setFont("Helvetica", 7)
pdf.drawString(15 * mm, 10 * mm, "Reference image: Google ARCore Augmented Images sample. Apache 2.0; licence bundled in the app.")
pdf.showPage()
pdf.save()
print(target)
