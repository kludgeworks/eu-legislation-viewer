// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The dismiss button of a receipt.
 *
 * A receipt says that the server did what the reader asked. Its button `button.dismiss` removes
 * the receipt from the page.
 */

/**
 * Removes the receipt of a dismiss button that the reader clicked.
 *
 * @param {Event} event a click on the page
 */
export const dismiss = (event) => {
  if (!(event.target instanceof Element)) {
    return;
  }
  event.target.closest(".receipt button.dismiss")?.closest(".receipt")?.remove();
};

document.addEventListener("click", dismiss);
