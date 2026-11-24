import { useRef, useState } from "react";
import { IconButton, InputAdornment, TextField, Tooltip } from "@mui/material";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";

export function CopyField({ label, value }: { label: string; value: string }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      if (navigator.clipboard) {
        await navigator.clipboard.writeText(value);
      } else {
        inputRef.current?.select();
        if (!document.execCommand("copy")) return;
      }
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      inputRef.current?.select();
    }
  }

  return (
    <TextField
      label={label}
      value={value}
      fullWidth
      size="small"
      inputRef={inputRef}
      onFocus={(e) => e.target.select()}
      slotProps={{
        input: {
          readOnly: true,
          sx: { fontFamily: "monospace" },
          endAdornment: (
            <InputAdornment position="end">
              <Tooltip title={copied ? "Copiado" : "Copiar"}>
                <IconButton edge="end" onClick={copy} aria-label={`Copiar ${label}`}>
                  <ContentCopyIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </InputAdornment>
          ),
        },
      }}
    />
  );
}
