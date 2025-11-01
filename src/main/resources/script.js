const el = (id) => document.getElementById(id);

// Control visibility using diplay type style
const computeVisibility = (visible, type = "block") =>
  visible ? type : "none";

// Glitch parameters visibility control
const shouldShowParams = (effect) => effect === "glitch";

// Form data
const createFormData = (file, data) => {
  const formData = new FormData();
  Object.entries(data).forEach(([k, v]) => formData.append(k, v));
  formData.append("file", file);
  return formData;
};

// Convertion Blob to Base64
const blobToBase64 = (blob) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onloadend = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });

// Session storage load and save
const saveSession = (key, value) => sessionStorage.setItem(key, value);
const loadSession = (key) => sessionStorage.getItem(key);

// Image download URL
const createDownloadLink = (dataUrl, filename = "image.png") => {
  const link = document.createElement("a");
  link.href = dataUrl;
  link.download = filename;
  return link;
};

// Render page objects
const renderParamsVisibility = (effect) => {
  const params = el("glitchParams");
  params.style.display = computeVisibility(shouldShowParams(effect), "flex");
};

const renderImage = (dataUrl) => {
  const img = el("output");
  const btn = el("downloadBtn");
  img.src = dataUrl;
  img.style.display = "block";
  btn.style.display = "inline-block";
};

const uploadImage = async () => {
  const file = el("fileInput").files[0];
  if (!file) return alert("Выбери изображение!");

  const effect = el("effect").value;
  const glitchShift = getClampedInputValue("glitchShift", 1, 100);
  const glitchIntensity = getClampedInputValue("glitchIntensity", 1, 100);

  const formData = createFormData(file, { effect, glitchShift, glitchIntensity });

  try {
    const response = await fetch("/process", { method: "POST", body: formData });

    // Server error handling
    if (!response.ok) {
      console.error(`Server error: ${response.status} ${response.statusText}`);
      alert("Ошибка при обработке изображения! Сервер не смог выполнить запрос.");
      return;
    }

    const blob = await response.blob();
    const imgURL = URL.createObjectURL(blob);
    renderImage(imgURL);

    // Save upgraded image
    sessionStorage.setItem("processedImageURL", imgURL);
    console.log("✅ Image processed and saved as blob URL");

  } catch (err) {
    console.error("Client/network error:", err);
    console.warn("⚠️ Ошибка сети или проблемы с подключением, но не с сервером.");
  }
};


// Download image
const downloadImage = () => {
  const saved = loadSession("processedImage");
  if (!saved) return;
  const link = createDownloadLink(saved, "transformed_image.png");
  link.click();
};

const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

// Imput value limitation
const getClampedInputValue = (id, min, max) => {
  const elInput = el(id);
  if (!elInput) return min;
  const val = parseInt(elInput.value, 10);
  const clamped = clamp(isNaN(val) ? min : val, min, max);
  elInput.value = clamped;
  return clamped;
};

const init = () => {
  const saved = loadSession("processedImage");
  if (saved) renderImage(saved);

  el("effect").addEventListener("change", (e) => renderParamsVisibility(e.target.value));
  el("processBtn").addEventListener("click", uploadImage);
  el("downloadBtn").addEventListener("click", downloadImage);
};

window.addEventListener("DOMContentLoaded", init);
