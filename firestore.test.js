const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserProfile(uid) {
  return {
    userId: uid,
    email: "shop@example.com",
    shopName: "Pulse Electronics",
    address: "123 Market Road",
    phone: "+919876543210",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validBusiness(uid, localId = 1) {
  return {
    userId: uid,
    localId,
    businessName: "Pulse Electronics",
    ownerName: "Owner",
    tagline: "Best Deals",
    phone: "9876543210",
    email: "shop@example.com",
    website: "https://hisab.example",
    address: "123 Market Road",
    city: "Mumbai",
    state: "Maharashtra",
    pinCode: "400001",
    category: "RETAIL",
    isGstRegistered: true,
    gstin: "27ABCDE1234F1Z5",
    financialYear: "FY 2026-27",
    currencySymbol: "₹",
    invoicePrefix: "INV-",
    nextInvoiceNumber: 1001,
    upiId: "shop@upi",
    signatureLabel: "Authorized Signatory",
    defaultTerms: "Thank you!",
    localUpdatedAt: 1700000000000,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validProduct(uid, localId = 1) {
  return {
    userId: uid,
    localId,
    businessId: 1,
    name: "USB-C Cable",
    sku: "USB-01",
    barcode: "8901234567890",
    productCode: "P001",
    hsnSacCode: "8544",
    categoryName: "Accessories",
    brand: "Hisab",
    description: "Fast cable",
    purchasePricePaise: 10000,
    sellingPricePaise: 25000,
    wholesalePricePaise: 18000,
    wholesaleMinQtyScaled: 5000,
    mrpPaise: 29900,
    taxRatePercentScaled: 1800,
    isTaxInclusive: false,
    unitName: "PCS",
    openingStockScaled: 20000,
    currentStockScaled: 20000,
    minStockScaled: 5000,
    batchNumber: "",
    expiryDateMillis: null,
    garmentSize: "",
    garmentColor: "",
    garmentStyle: "",
    serialNumber: "",
    warrantyMonths: 6,
    isServiceItem: false,
    isRawMaterial: false,
    defaultDiscountPercentScaled: 0,
    trackExpiry: false,
    isActive: true,
    localCreatedAt: 1700000000000,
    localUpdatedAt: 1700000000000,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validInvoice(uid, localId = 1) {
  return {
    userId: uid,
    localId,
    businessId: 1,
    invoiceNumber: "INV-1001",
    documentType: "TAX_INVOICE",
    status: "FINALIZED",
    invoiceDateMillis: 1700000000000,
    dueDateMillis: null,
    customerId: 1,
    customerName: "Walk-in Customer",
    customerPhone: "",
    customerAddress: "",
    customerGstin: "",
    isInterState: false,
    subtotalPaise: 25000,
    itemDiscountPaise: 0,
    invoiceDiscountPercentScaled: 0,
    invoiceDiscountFlatPaise: 0,
    taxableAmountPaise: 25000,
    cgstPaise: 2250,
    sgstPaise: 2250,
    igstPaise: 0,
    totalTaxPaise: 4500,
    additionalChargesPaise: 0,
    shippingChargesPaise: 0,
    roundOffPaise: 0,
    grandTotalPaise: 29500,
    paidAmountPaise: 29500,
    balanceDuePaise: 0,
    paymentMode: "CASH",
    paymentReference: "",
    loyaltyPointsEarned: 2,
    loyaltyPointsRedeemed: 0,
    loyaltyDiscountPaise: 0,
    tableNumber: "",
    kotStatus: "",
    linkedOriginalInvoiceId: null,
    notes: "",
    termsAndConditions: "Thank you!",
    localCreatedAt: 1700000000000,
    localUpdatedAt: 1700000000000,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validInvoiceItem(uid, invoiceLocalId = 1) {
  return {
    userId: uid,
    invoiceLocalId,
    productId: 1,
    batchId: null,
    productName: "USB-C Cable",
    productCode: "P001",
    hsnSacCode: "8544",
    unitName: "PCS",
    quantityScaled: 1000,
    ratePaise: 25000,
    purchaseCostPaise: 10000,
    discountPercentScaled: 0,
    discountAmountPaise: 0,
    taxRatePercentScaled: 1800,
    isTaxInclusive: false,
    taxableAmountPaise: 25000,
    cgstPaise: 2250,
    sgstPaise: 2250,
    igstPaise: 0,
    lineTotalPaise: 29500,
    batchNumber: "",
    expiryDateMillis: null,
    serialNumber: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user collections", async () => {
  const db = testEnv.unauthenticatedContext().firestore();
  await assertFails(db.collection("users").doc(ALICE_UID).get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("businesses").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(db.collection("users").doc(ALICE_UID).collection("invoices").get());
  await assertFails(
    db.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set(validProduct(ALICE_UID))
  );
});

test("Cross-user access is denied for profile, business, product, and invoice", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserProfile(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("businesses").doc("biz_1").set(validBusiness(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").set(validProduct(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_1").set(validInvoice(ALICE_UID))
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("businesses").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1").update({ sellingPricePaise: 100 })
  );
});

test("Owner can create and update business, product, invoice, and invoice items cleanly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(userRef.set(validUserProfile(ALICE_UID)));
  await assertSucceeds(userRef.collection("businesses").doc("biz_1").set(validBusiness(ALICE_UID)));
  await assertSucceeds(
    userRef.collection("businesses").doc("biz_1").update({
      businessName: "Pulse Supermart",
      nextInvoiceNumber: 1002,
      localUpdatedAt: 1700000005000,
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(userRef.collection("products").doc("prod_1").set(validProduct(ALICE_UID)));
  await assertSucceeds(
    userRef.collection("products").doc("prod_1").update({
      currentStockScaled: 18000,
      localUpdatedAt: 1700000005000,
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(userRef.collection("invoices").doc("inv_1").set(validInvoice(ALICE_UID)));
  await assertSucceeds(
    userRef.collection("invoices").doc("inv_1").collection("items").doc("item_0").set(validInvoiceItem(ALICE_UID))
  );
  await assertSucceeds(
    userRef.collection("invoices").doc("inv_1").update({
      status: "PAID",
      paidAmountPaise: 29500,
      balanceDuePaise: 0,
      localUpdatedAt: 1700000005000,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    userRef.collection("invoices").doc("inv_1").collection("items").doc("item_0").update({
      quantityScaled: 2000,
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(userRef.collection("businesses").get());
  await assertSucceeds(userRef.collection("products").get());
  await assertSucceeds(userRef.collection("invoices").get());
  await assertSucceeds(userRef.collection("invoices").doc("inv_1").collection("items").get());
});

test("Validation rejects spoofed userId, invalid enums, oversized names, and createdAt tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userRef = aliceDb.collection("users").doc(ALICE_UID);

  // Spoofed userId
  await assertFails(
    userRef.collection("products").doc("prod_1").set(validProduct(BOB_UID))
  );

  // Invalid business category enum
  await assertFails(
    userRef.collection("businesses").doc("biz_1").set({
      ...validBusiness(ALICE_UID),
      category: "INVALID_CATEGORY",
    })
  );

  // Invalid invoice status enum
  await assertFails(
    userRef.collection("invoices").doc("inv_1").set({
      ...validInvoice(ALICE_UID),
      status: "HACKED_STATUS",
    })
  );

  // Negative sellingPricePaise
  await assertFails(
    userRef.collection("products").doc("prod_1").set({
      ...validProduct(ALICE_UID),
      sellingPricePaise: -500,
    })
  );

  // Mutating immutable localId / createdAt on update
  await assertSucceeds(userRef.collection("products").doc("prod_1").set(validProduct(ALICE_UID)));
  await assertFails(
    userRef.collection("products").doc("prod_1").update({
      localId: 999,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    userRef.collection("products").doc("prod_1").update({
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});
