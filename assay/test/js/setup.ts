import "@testing-library/jest-dom";

// jsdom does not implement HTMLDialogElement.showModal / .close.
// These stubs are enough for component tests to run.
HTMLDialogElement.prototype.showModal = function () {
  this.setAttribute("open", "");
};
HTMLDialogElement.prototype.close = function () {
  this.removeAttribute("open");
};
