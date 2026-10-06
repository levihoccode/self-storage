import { useEffect, useRef } from "react";

/**
 * Minimum a11y baseline shared by every overlay dialog (DetailPanel,
 * ConfirmDialog, ComingSoonDialog): focuses the dialog when it opens, closes
 * it on Escape, and restores focus to whatever was focused before it opened.
 * Not a full focus trap (Tab can still escape the dialog) — enough for a
 * prototype without pulling in a focus-trap library.
 */
export function useModalA11y<T extends HTMLElement>(onClose: () => void) {
  const containerRef = useRef<T>(null);

  useEffect(() => {
    const previouslyFocused = document.activeElement as HTMLElement | null;
    containerRef.current?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      previouslyFocused?.focus();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return containerRef;
}
