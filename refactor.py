import os
import re

def process_file(filepath, replacements, imports_to_add):
    with open(filepath, 'r') as f:
        content = f.read()

    original_content = content
    for old, new in replacements.items():
        content = content.replace(old, new)

    if content != original_content:
        # Add imports if they don't exist
        for imp in imports_to_add:
            import_statement = f"import {imp};\n"
            if import_statement not in content and f"import {imp}" not in content:
                # Find last import
                last_import_idx = content.rfind("import ")
                if last_import_idx != -1:
                    end_of_line = content.find("\n", last_import_idx)
                    content = content[:end_of_line+1] + import_statement + content[end_of_line+1:]
                else:
                    # Find package
                    pkg_idx = content.find("package ")
                    if pkg_idx != -1:
                        end_of_line = content.find("\n", pkg_idx)
                        content = content[:end_of_line+1] + "\n" + import_statement + content[end_of_line+1:]
        
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Updated {filepath}")
    else:
        print(f"No changes in {filepath}")

# 1. RelatorioProdutividadeVM.java
process_file(
    "src/main/java/com/uem/extrator/viewmodel/RelatorioProdutividadeVM.java",
    {"java.nio.charset.StandardCharsets.UTF_8": "StandardCharsets.UTF_8"},
    ["java.nio.charset.StandardCharsets"]
)

# 2. ExtratorVM.java
process_file(
    "src/main/java/com/uem/extrator/viewmodel/ExtratorVM.java",
    {
        "java.time.Year": "Year",
        "java.util.concurrent.atomic.AtomicInteger": "AtomicInteger",
        "java.util.Date": "Date",
        "java.util.concurrent.Executors": "Executors",
        "java.util.concurrent.ConcurrentLinkedQueue": "ConcurrentLinkedQueue",
        "java.io.ByteArrayInputStream": "ByteArrayInputStream",
        "java.util.zip.ZipInputStream": "ZipInputStream",
        "java.util.zip.ZipEntry": "ZipEntry",
        "java.io.ByteArrayOutputStream": "ByteArrayOutputStream",
        "java.util.regex.Matcher": "Matcher",
        "java.util.regex.Pattern": "Pattern"
    },
    [
        "java.time.Year",
        "java.util.concurrent.atomic.AtomicInteger",
        "java.util.Date",
        "java.util.concurrent.Executors",
        "java.util.concurrent.ConcurrentLinkedQueue",
        "java.io.ByteArrayInputStream",
        "java.util.zip.ZipInputStream",
        "java.util.zip.ZipEntry",
        "java.io.ByteArrayOutputStream",
        "java.util.regex.Matcher",
        "java.util.regex.Pattern"
    ]
)

# 3. EmailService.java
process_file(
    "src/main/java/com/uem/extrator/service/EmailService.java",
    {"java.util.List<String>": "List<String>"},
    ["java.util.List"]
)

# 4. AuditLogService.java
process_file(
    "src/main/java/com/uem/extrator/service/AuditLogService.java",
    {"java.util.Calendar": "Calendar"},
    ["java.util.Calendar"]
)

# 5. LattesParser.java
process_file(
    "src/main/java/com/uem/extrator/service/LattesParser.java",
    {
        "java.util.regex.Matcher": "Matcher",
        "java.util.regex.Pattern": "Pattern"
    },
    [
        "java.util.regex.Matcher",
        "java.util.regex.Pattern"
    ]
)

# 6. SemanticScholarService.java
process_file(
    "src/main/java/com/uem/extrator/service/SemanticScholarService.java",
    {
        "java.net.Proxy": "Proxy",
        "java.lang.reflect.Method": "Method"
    },
    [
        "java.net.Proxy",
        "java.lang.reflect.Method"
    ]
)

# 7. AutomacaoService.java
process_file(
    "src/main/java/com/uem/extrator/service/AutomacaoService.java",
    {"new java.util.Date()": "new Date()"},
    ["java.util.Date"]
)
