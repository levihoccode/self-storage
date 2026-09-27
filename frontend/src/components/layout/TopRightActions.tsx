import { ChevronDown, LogOut } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { authGateway } from "../../app/auth";
import type { Theme } from "../ui/ThemeToggle";
import { ThemeToggle } from "../ui/ThemeToggle";

export function TopRightActions({
  theme,
  onThemeToggle,
  onLogout,
  onViewProfile,
  profileRoleLabel = "Khách hàng",
}: {
  theme: Theme;
  onThemeToggle: () => void;
  onLogout: () => void;
  onViewProfile?: () => void;
  profileRoleLabel?: string;
}) {
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const session = authGateway.getSession();
  const displayName = session?.user.name ?? "Người dùng";
  const displayEmail = session?.user.email ?? "user@example.com";

  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (profileRef.current && !profileRef.current.contains(event.target as Node)) {
        setProfileOpen(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setProfileOpen(false);
    }

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, []);

  return (
    <div className="top-right-actions">
      <ThemeToggle theme={theme} onToggle={onThemeToggle} />

      <button type="button" className="top-right-logout" onClick={onLogout}>
        <LogOut size={15} />
        <span>Đăng xuất</span>
      </button>

      <div className="top-right-profile" ref={profileRef}>
        <button
          type="button"
          className={`top-right-user ${profileOpen ? "is-open" : ""}`}
          onClick={() => setProfileOpen((current) => !current)}
          aria-expanded={profileOpen}
          aria-haspopup="menu"
        >
          <span className="top-right-avatar">{displayName.slice(0, 1)}</span>
          <span className="top-right-user-copy">
            <strong>{displayName}</strong>
            <small>{profileRoleLabel}</small>
          </span>
          <ChevronDown size={16} />
        </button>

        {profileOpen && (
          <div className="top-right-profile-menu" role="menu">
            <div className="top-right-profile-heading">
              <strong>{displayName}</strong>
              <span>{displayEmail}</span>
            </div>

            <button
              type="button"
              role="menuitem"
              onClick={() => {
                setProfileOpen(false);
                if (onViewProfile) onViewProfile();
              }}
            >
              Thông tin của tôi
            </button>

            <button type="button" role="menuitem" className="profile-logout" onClick={onLogout}>
              <LogOut size={16} />
              <span>Đăng xuất</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
