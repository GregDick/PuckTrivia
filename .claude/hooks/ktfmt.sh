#!/bin/bash
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // .tool_input.filePath // empty')

if [[ "$FILE_PATH" =~ \.kt$ ]]; then
  ktfmt --kotlinlang-style "$FILE_PATH" 2>&1
fi

exit 0
