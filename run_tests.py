import subprocess
import os
import glob
import xml.etree.ElementTree as ET

MAVEN = r"C:\apache-maven-3.9.6\bin\mvn.cmd"

REPORT_DIR = "target/surefire-reports"
OUTPUT_FILE = "test_results.txt"

print("Running Maven tests...\n")

process = subprocess.Popen(
    [MAVEN, "test"],
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
    text=True,
    encoding="utf-8",
    errors="ignore"
)

# Print Maven output live
for line in process.stdout:
    print(line, end="")

process.wait()

print("\nFinished running tests.\n")

if not os.path.exists(REPORT_DIR):
    print(f"Report directory not found: {REPORT_DIR}")
    exit(1)

xml_files = glob.glob(os.path.join(REPORT_DIR, "*.xml"))

if not xml_files:
    print("No XML test reports found.")
    exit(1)

with open(OUTPUT_FILE, "w", encoding="utf-8") as output:

    for xml_file in xml_files:

        tree = ET.parse(xml_file)
        root = tree.getroot()

        for testcase in root.findall("testcase"):

            classname = testcase.attrib.get("classname", "")
            name = testcase.attrib.get("name", "")

            failure = testcase.find("failure")
            error = testcase.find("error")
            skipped = testcase.find("skipped")

            test_name = f"{classname}.{name}"

            if failure is not None:
                output.write(f"{test_name}: FAILED\n")

                message = failure.attrib.get("message", "")
                if message:
                    output.write(f"Reason: {message}\n")
                else:
                    output.write(f"Reason:\n{failure.text}\n")

                output.write("-" * 80 + "\n")

            elif error is not None:
                output.write(f"{test_name}: ERROR\n")

                message = error.attrib.get("message", "")
                if message:
                    output.write(f"Reason: {message}\n")
                else:
                    output.write(f"Reason:\n{error.text}\n")

                output.write("-" * 80 + "\n")

            elif skipped is not None:
                output.write(f"{test_name}: SKIPPED\n")
                output.write("-" * 80 + "\n")

            else:
                output.write(f"{test_name}: PASSED\n")
                output.write("-" * 80 + "\n")

print(f"\nResults saved to '{OUTPUT_FILE}'")

