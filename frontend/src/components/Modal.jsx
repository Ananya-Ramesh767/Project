import React, { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';

export default function Modal({ isOpen, onClose, title, children, maxWidth = '580px' }) {
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return createPortal(
    <div
      className="modal-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className="modal-dialog glass-panel"
        style={{ maxWidth, margin: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        {title && (
          <div className="modal-header">
            <h2 className="modal-title">{title}</h2>
            <button
              type="button"
              className="btn btn-secondary"
              style={{ padding: '0.25rem 0.5rem', fontSize: '0.85rem' }}
              onClick={onClose}
              aria-label="Close"
            >
              <X size={16} />
            </button>
          </div>
        )}
        <div className="modal-body">
          {children}
        </div>
      </div>
    </div>,
    document.body
  );
}
