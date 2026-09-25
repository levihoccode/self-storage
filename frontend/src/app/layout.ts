/**
 * The page-width rule the prototype has always used: 1200px with 24px desktop
 * gutters, 600px with 16px mobile gutters.
 *
 * It is an explicit utility rather than Tailwind's `container` class because
 * that class adds its own breakpoint max-widths (up to 1536px) and silently
 * widens every shell that uses it.
 */
export const PAGE_CONTAINER =
  "mx-auto w-[min(1200px,calc(100%-48px))] max-[760px]:w-[min(100%-32px,600px)]";
