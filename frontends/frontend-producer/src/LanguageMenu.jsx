const LANGUAGE_NAMES = { de: "Deutsch", fr: "Français", it: "Italiano", en: "English" };

function LanguageMenu() {
  const t = useT();
  const language = I18n.getLanguage();
  const languages = I18n.SUPPORTED_LANGS;
  const [open, setOpen] = useState(false);
  const containerRef = useRef(null);
  const buttonRef = useRef(null);
  const itemRefs = useRef([]);

  useEffect(() => {
    if (!open) return;
    itemRefs.current[languages.indexOf(language)]?.focus();
    const closeOnOutsideClick = (e) => {
      if (!containerRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener("mousedown", closeOnOutsideClick);
    return () => document.removeEventListener("mousedown", closeOnOutsideClick);
  }, [open]);

  const close = ({ restoreFocus }) => {
    setOpen(false);
    if (restoreFocus) buttonRef.current.focus();
  };

  const choose = (lng) => {
    I18n.chooseLanguage(lng);
    close({ restoreFocus: true });
  };

  const focusItem = (index) => {
    itemRefs.current[(index + languages.length) % languages.length].focus();
  };

  const onButtonKeyDown = (e) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      setOpen(true);
    }
  };

  const onMenuKeyDown = (e) => {
    const index = itemRefs.current.indexOf(document.activeElement);
    if (e.key === "ArrowDown") { e.preventDefault(); focusItem(index + 1); }
    else if (e.key === "ArrowUp") { e.preventDefault(); focusItem(index - 1); }
    else if (e.key === "Home") { e.preventDefault(); focusItem(0); }
    else if (e.key === "End") { e.preventDefault(); focusItem(languages.length - 1); }
    else if (e.key === "Escape") { e.preventDefault(); close({ restoreFocus: true }); }
    else if (e.key === "Tab") { close({ restoreFocus: false }); }
  };

  return (
    <div className="lang-menu" ref={containerRef}>
      <button
        ref={buttonRef}
        type="button"
        className="lang-menu-button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={t("topbar.languageButton", { language: LANGUAGE_NAMES[language] })}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
        onKeyDown={onButtonKeyDown}>
        <span>{LANGUAGE_NAMES[language]}</span>
        <Icon.ChevronDown />
      </button>
      {open &&
        <ul className="lang-menu-list" role="menu" aria-label={t("topbar.language")} onKeyDown={onMenuKeyDown}>
          {languages.map((lng, i) =>
            <li key={lng} role="none">
              <button
                ref={(el) => { itemRefs.current[i] = el; }}
                type="button"
                role="menuitemradio"
                aria-checked={lng === language}
                lang={lng}
                tabIndex={-1}
                className="lang-menu-item"
                onClick={() => choose(lng)}>
                <span className="lang-code">{lng.toUpperCase()}</span>
                <span>{LANGUAGE_NAMES[lng]}</span>
                {lng === language && <Icon.Check />}
              </button>
            </li>
          )}
        </ul>
      }
    </div>);

}

window.LanguageMenu = LanguageMenu;
