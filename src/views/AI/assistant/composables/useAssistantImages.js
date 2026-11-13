import { watch } from 'vue';

export function useAssistantImages({ state, services, runtime, actions }) {
  const {
    imageAttachment, imageConsent, imageConsentRemembered, imageBusy, imageError,
    composerRef, messages, busy, capability,
  } = state;
  const { prepareAiImage, showImagePreview } = services;
  let imageViewer;

  const stopConsentWatch = watch(imageConsentRemembered, value => {
    if (value && runtime.isActive()) imageConsent.value = true;
  });

  function clearImage() {
    imageAttachment.value = null;
    imageConsent.value = false;
    imageError.value = '';
    composerRef.value?.clearFile();
  }

  function previewImage(dataUrl) {
    if (!runtime.isActive()) return;
    imageViewer = showImagePreview({ images: [dataUrl], closeable: true });
  }

  async function selectImage(event) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!runtime.isActive() || !file || busy.value || capability.value?.ocrEnabled !== true) return;
    clearImage();
    imageBusy.value = true;
    try {
      const prepared = await prepareAiImage(file);
      if (runtime.isActive()) imageAttachment.value = prepared;
    } catch (error) {
      if (runtime.isActive()) imageError.value = error?.message || '无法处理图片，请换一张清晰截图';
    } finally {
      if (runtime.isActive()) imageBusy.value = false;
    }
  }

  function releaseImages() {
    imageViewer?.close();
    imageViewer = null;
    for (const item of messages.value) {
      delete item.imagePreview;
      delete item.requestImage;
    }
  }

  function initialize() {}

  function dispose() {
    stopConsentWatch();
    clearImage();
    releaseImages();
    imageConsentRemembered.value = false;
  }

  return { clearImage, previewImage, selectImage, releaseImages, initialize, dispose };
}
