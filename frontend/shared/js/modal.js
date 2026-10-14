window.Modal = (function () {

  function open({ title, body, size = 'md', noPad = false }) {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.innerHTML = `
      <div class="modal-box modal-${size}">
        <header class="modal-head">
          <h3>${Utils.esc(title || '')}</h3>
          <button class="modal-x" aria-label="Close">×</button>
        </header>
        <div class="modal-body${noPad ? ' no-pad' : ''}">${body || ''}</div>
      </div>
    `;
    document.body.appendChild(overlay);

    // animate in
    void overlay.offsetWidth;
    overlay.classList.add('modal-in');

    function close() {
      overlay.classList.remove('modal-in');
      document.removeEventListener('keydown', onKey);
      setTimeout(() => overlay.remove(), 180);
    }

    function onKey(e) { if (e.key === 'Escape') close(); }

    overlay.querySelector('.modal-x').onclick = close;
    overlay.onclick = (e) => { if (e.target === overlay) close(); };
    document.addEventListener('keydown', onKey);

    return { close, el: overlay };
  }

  return { open };
})();