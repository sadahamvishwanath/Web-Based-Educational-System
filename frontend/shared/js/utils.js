window.Utils = (function () {
  function esc(s) {
    if (s == null) return '';
    return String(s).replace(/[&<>"']/g, c => ({
      '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'
    }[c]));
  }

  function initials(name) {
    if (!name) return '?';
    return name.split(' ').filter(Boolean).slice(0, 2).map(w => w[0].toUpperCase()).join('');
  }

  function fmtMoney(n) {
    if (n == null) return '';
    return 'Rs. ' + Number(n).toFixed(2);
  }

  return { esc, initials, fmtMoney };
})();