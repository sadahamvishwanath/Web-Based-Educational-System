window.Shell = (function () {

  const NAV = {
    STUDENT: [
      { section: 'Main' },
      { label: 'Dashboard',  href: 'dashboard.html',  icon: 'dashboard', key: 'dashboard' },
      { label: 'Timetable',  href: 'timetable.html',  icon: 'calendar',  key: 'timetable' },
      { label: 'My Classes', href: 'classes.html',    icon: 'book',      key: 'classes' },
      { section: 'Learning' },
      { label: 'Materials',  href: 'materials.html',  icon: 'file',      key: 'materials' },
      { label: 'Exams',      href: 'exams.html',      icon: 'exam',      key: 'exams' },
      { label: 'Attendance', href: 'attendance.html', icon: 'clipboard', key: 'attendance' },
      { section: 'Account' },
      { label: 'My Profile', href: 'profile.html',    icon: 'user',      key: 'profile' },
    ],
    PARENT: [
      { section: 'Main' },
      { label: 'Dashboard',   href: 'dashboard.html', icon: 'dashboard', key: 'dashboard' },
      { label: 'My Children', href: 'children.html',  icon: 'user',      key: 'children' },
      { section: 'Finance' },
      { label: 'Invoices',    href: 'invoices.html',  icon: 'money',     key: 'invoices' },
      { section: 'Account' },
      { label: 'My Profile',  href: 'profile.html',   icon: 'user',      key: 'profile' },
    ],
    TEACHER: [
      { section: 'Main' },
      { label: 'Dashboard',   href: 'dashboard.html',  icon: 'dashboard', key: 'dashboard' },
      { label: 'My Classes',  href: 'classes.html',    icon: 'book',      key: 'classes' },
      { section: 'Teaching' },
      { label: 'Attendance',  href: 'attendance.html', icon: 'clipboard', key: 'attendance' },
      { label: 'Materials',   href: 'materials.html',  icon: 'file',      key: 'materials' },
      { label: 'Exams',       href: 'exams.html',      icon: 'exam',      key: 'exams' },
      { section: 'Personal' },
      { label: 'My Salary',   href: 'salary.html',     icon: 'money',     key: 'salary' },
      { label: 'My Profile',  href: 'profile.html',    icon: 'user',      key: 'profile' },
    ],
    ADMIN: [
      { section: 'Overview' },
      { label: 'Dashboard',  href: 'dashboard.html',  icon: 'dashboard', key: 'dashboard' },
      { section: 'People' },
      { label: 'Users',      href: 'users.html',      icon: 'user',      key: 'users' },
      { label: 'Students',   href: 'students.html',   icon: 'award',     key: 'students' },
      { label: 'Attendance', href: 'attendance.html', icon: 'clipboard', key: 'attendance' },
      { section: 'Academics' },
      { label: 'Subjects',   href: 'subjects.html',   icon: 'book',      key: 'subjects' },
      { label: 'Rooms',      href: 'rooms.html',      icon: 'pin',       key: 'rooms' },
      { label: 'Classes',    href: 'classes.html',    icon: 'calendar',  key: 'classes' },
      { label: 'Timetable',  href: 'timetable.html',  icon: 'clock',     key: 'timetable' },
      { section: 'Finance' },
      { label: 'Finance',    href: '../management/dashboard.html', icon: 'money', key: 'finance' },
      { section: 'Communication' },
      { label: 'Notifications', href: 'notifications.html', icon: 'bell', key: 'notifications' },
      { section: 'Account' },
      { label: 'My Profile',   href: 'profile.html',        icon: 'user', key: 'profile' },
    ],
    MANAGEMENT: [
      { section: 'Overview' },
      { label: 'Dashboard',  href: 'dashboard.html',       icon: 'dashboard', key: 'dashboard' },
      { section: 'Finance' },
      { label: 'Fee Setup',  href: 'fee-structures.html',  icon: 'file',      key: 'fee-structures' },
      { label: 'Invoices',   href: 'invoices.html',        icon: 'money',     key: 'invoices' },
      { label: 'Payments',   href: 'payments.html',        icon: 'money',     key: 'payments' },
      { label: 'Salaries',   href: 'salaries.html',        icon: 'user',      key: 'salaries' },
      { section: 'Reporting' },
      { label: 'Reports',    href: 'reports.html',         icon: 'trend',     key: 'reports' },
      { section: 'Account' },
      { label: 'My Profile', href: 'profile.html',         icon: 'user',      key: 'profile' },
    ],
  };

  const FALLBACK_NAMES = {
    STUDENT:    'John Smith',
    PARENT:     'Michael Smith',
    TEACHER:    'David Wilson',
    ADMIN:      'System Admin',
    MANAGEMENT: 'Finance Manager',
  };

  function getUser(roleOverride) {
    let stored = null;
    try { stored = JSON.parse(localStorage.getItem('apex_user') || 'null'); } catch (e) {}

    if (roleOverride) {
      if (stored) { stored.role = roleOverride; return stored; }
      return { fullName: FALLBACK_NAMES[roleOverride] || 'Guest', role: roleOverride };
    }
    if (stored) return stored;
    return { fullName: FALLBACK_NAMES.STUDENT, role: 'STUDENT' };
  }

  function render({ active, content, role }) {
    const user = getUser(role);
    const navItems = NAV[user.role] || [];
    const roleLabel = {
      STUDENT: 'Student', PARENT: 'Parent', TEACHER: 'Teacher',
      ADMIN: 'Admin', MANAGEMENT: 'Management',
    }[user.role] || user.role;

    const navHtml = navItems.map(item => {
      if (item.section) return `<div class="nav-section-label">${Utils.esc(item.section)}</div>`;
      const iconFn = Icon[item.icon] || Icon.dashboard;
      const isActive = item.key === active;
      return `<a href="${item.href}" class="nav-item${isActive ? ' active' : ''}">
                ${iconFn()}
                <span>${Utils.esc(item.label)}</span>
              </a>`;
    }).join('');

    document.body.innerHTML = `
      <div class="app-shell">
        <aside class="app-sidebar">
          <div class="sidebar-brand">
            <div class="brand-mark">A</div>
            <div>
              <div class="brand-text">Apex Institute</div>
              <div class="brand-sub">Learning Platform</div>
            </div>
          </div>
          <nav class="sidebar-nav">${navHtml}</nav>
          <div class="sidebar-footer">© 2026 Apex Institute</div>
        </aside>

        <div class="app-main-area">
          <header class="app-header">
            
            <div class="header-spacer"></div>
            <div class="header-actions">
              
              <div class="header-divider"></div>
              <a href="#" class="user-chip">
                <div class="user-chip-info">
                  <div class="user-chip-name">${Utils.esc(user.fullName)}</div>
                  <div class="user-chip-role">${Utils.esc(roleLabel)}</div>
                </div>
                <div class="user-avatar">${Utils.initials(user.fullName)}</div>
              </a>
              <button class="icon-btn" title="Log out" onclick="try{localStorage.removeItem('apex_user');localStorage.removeItem('apex_role');localStorage.removeItem('apex_token');}catch(e){};location.href='../login.html';">
                ${Icon.logout()}
              </button>
            </div>
          </header>

          <main id="app-main">${content}</main>
        </div>
      </div>
    `;

    const main = document.getElementById('app-main');
    main.style.animation = 'none';
    void main.offsetWidth;
    main.style.animation = '';
  }

  function currentRole() {
    try {
      const u = JSON.parse(localStorage.getItem('apex_user') || 'null');
      return u && u.role ? u.role : null;
    } catch (e) { return null; }
  }

  return { render, currentRole };
})();