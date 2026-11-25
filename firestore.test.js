const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_shop_123";
const BOB_UID = "bob_shop_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write any shop data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("shops").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("shops")
      .doc(ALICE_UID)
      .collection("products")
      .get()
  );
  await assertFails(
    unauthDb
      .collection("shops")
      .doc(ALICE_UID)
      .set({
        userId: ALICE_UID,
        shopName: "Hack Shop",
        language: "rw",
        monthlyReceiptLimit: 60,
        receiptsUsedThisMonth: 0,
        receiptMonthKey: "2026-11",
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Owner can create and update shop profile; rejects invalid keys and cross-user access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb
      .collection("shops")
      .doc(ALICE_UID)
      .set({
        userId: ALICE_UID,
        shopName: "Kigali Fresh Market",
        phone: "+250788123456",
        language: "rw",
        monthlyReceiptLimit: 60,
        receiptsUsedThisMonth: 0,
        receiptMonthKey: "2026-11",
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("shops")
      .doc(ALICE_UID)
      .update({
        shopName: "Kigali Fresh Mart",
        language: "en",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject ghost field / shadow update
  await assertFails(
    aliceDb
      .collection("shops")
      .doc(ALICE_UID)
      .update({
        isAdmin: true,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb
      .collection("shops")
      .doc(ALICE_UID)
      .update({
        createdAt: new Date(Date.now() - 100),
        updatedAt: new Date(Date.now() - 100),
      })
  );

  // Reject Bob reading or writing Alice's shop
  await assertFails(bobDb.collection("shops").doc(ALICE_UID).get());
  await assertFails(
    bobDb
      .collection("shops")
      .doc(ALICE_UID)
      .update({ shopName: "Stolen", updatedAt: new Date(Date.now() - 500) })
  );
});

test("Products, immutable StockMovements, and cancellable Sales enforce parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  const shopRef = aliceDb.collection("shops").doc(ALICE_UID);

  // Create product (no 'id' field inside map)
  await assertSucceeds(
    shopRef.collection("products").doc("prod_1").set({
      userId: ALICE_UID,
      name: "Inyange Milk 1L",
      unit: "bottle",
      buyingPrice: 900,
      normalPrice: 1200,
      minStockLevel: 10,
      cachedStock: 24,
      supplierName: "Inyange Depot",
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject redundant 'id' field
  await assertFails(
    shopRef.collection("products").doc("prod_2").set({
      id: "prod_2",
      userId: ALICE_UID,
      name: "Sugar 1kg",
      unit: "kg",
      buyingPrice: 1400,
      normalPrice: 1700,
      minStockLevel: 5,
      cachedStock: 10,
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // List products as owner succeeds; cross-user list fails
  await assertSucceeds(shopRef.collection("products").get());
  await assertFails(
    bobDb.collection("shops").doc(ALICE_UID).collection("products").get()
  );

  // Create immutable stock movement
  await assertSucceeds(
    shopRef.collection("stockMovements").doc("mov_1").set({
      userId: ALICE_UID,
      productId: "prod_1",
      type: "bought",
      quantityDelta: 24,
      unitCost: 900,
      reason: "Initial stock",
      dateKey: "2026-11-22",
      createdAt: ts,
    })
  );

  // StockMovements are strictly immutable (update & delete denied)
  await assertFails(
    shopRef.collection("stockMovements").doc("mov_1").update({ quantityDelta: 100 })
  );
  await assertFails(
    shopRef.collection("stockMovements").doc("mov_1").delete()
  );

  // Create completed sale
  await assertSucceeds(
    shopRef.collection("sales").doc("sale_1").set({
      userId: ALICE_UID,
      productId: "prod_1",
      productName: "Inyange Milk 1L",
      quantity: 2,
      unitPrice: 1200,
      buyingPriceAtSale: 900,
      totalAmount: 2400,
      profitAmount: 600,
      paymentMethod: "momo",
      status: "completed",
      dateKey: "2026-11-22",
      monthKey: "2026-11",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cancel sale (completed -> cancelled) succeeds
  await assertSucceeds(
    shopRef.collection("sales").doc("sale_1").update({
      status: "cancelled",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Re-activating cancelled sale or deleting sale fails
  await assertFails(
    shopRef.collection("sales").doc("sale_1").update({
      status: "completed",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(shopRef.collection("sales").doc("sale_1").delete());
});

test("Debts, DebtPayments, and Receipts enforce state and immutability rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 4000);
  const shopRef = aliceDb.collection("shops").doc(ALICE_UID);

  await assertSucceeds(
    shopRef.collection("debts").doc("debt_1").set({
      userId: ALICE_UID,
      direction: "owed_to_me",
      partyName: "Jean Mugisha",
      partyPhone: "+250788000111",
      description: "2x Inyange Milk 1L",
      originalAmount: 2400,
      paidAmount: 0,
      status: "open",
      dueDateKey: "2026-11-25",
      relatedSaleId: "sale_1",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Partial payment update succeeds
  await assertSucceeds(
    shopRef.collection("debts").doc("debt_1").update({
      paidAmount: 1400,
      status: "open",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cannot overpay past originalAmount
  await assertFails(
    shopRef.collection("debts").doc("debt_1").update({
      paidAmount: 3000,
      status: "paid",
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Mark paid
  await assertSucceeds(
    shopRef.collection("debts").doc("debt_1").update({
      paidAmount: 2400,
      status: "paid",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Once paid, debt cannot be edited further
  await assertFails(
    shopRef.collection("debts").doc("debt_1").update({
      paidAmount: 0,
      status: "open",
      updatedAt: new Date(Date.now() - 700),
    })
  );

  // Create immutable debtPayment & receipt
  await assertSucceeds(
    shopRef.collection("debtPayments").doc("pay_1").set({
      userId: ALICE_UID,
      debtId: "debt_1",
      amount: 1400,
      method: "cash",
      dateKey: "2026-11-22",
      createdAt: ts,
    })
  );
  await assertFails(shopRef.collection("debtPayments").doc("pay_1").delete());

  await assertSucceeds(
    shopRef.collection("receipts").doc("rec_1").set({
      userId: ALICE_UID,
      supplierName: "Nyabugogo Wholesale",
      itemCount: 4,
      totalAmount: 48000,
      paymentMethod: "cash",
      monthKey: "2026-11",
      createdAt: ts,
    })
  );
  await assertFails(shopRef.collection("receipts").doc("rec_1").delete());

  // Reject future timestamp spoofing
  await assertFails(
    shopRef.collection("receipts").doc("rec_future").set({
      userId: ALICE_UID,
      itemCount: 1,
      totalAmount: 1000,
      paymentMethod: "cash",
      monthKey: "2026-11",
      createdAt: new Date(Date.now() + 120000),
    })
  );
});
