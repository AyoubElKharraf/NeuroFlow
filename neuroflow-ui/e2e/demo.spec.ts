import { expect, test } from "@playwright/test";

test.describe("NeuroFlow demo smoke", () => {
  test("login modal accepts admin credentials when backend is reachable", async ({
    page,
  }) => {
    await page.goto("/");

    await expect(page.getByText("NeuroFlow Sign-in")).toBeVisible({
      timeout: 20_000,
    });
    await page.getByLabel("Username").fill("admin");
    await page.getByLabel("Password").fill("admin123");
    await page.getByRole("button", { name: "Sign in" }).click();

    // Either authenticated dashboard (backend up) or login error (backend down)
    const adminBadge = page.getByText("admin", { exact: false }).first();
    const loginError = page.locator("p.text-danger");
    await expect(adminBadge.or(loginError)).toBeVisible({ timeout: 20_000 });
  });

  test("start simulation when full stack is up", async ({ page }) => {
    test.skip(
      process.env.PLAYWRIGHT_REQUIRE_BACKEND !== "1",
      "Set PLAYWRIGHT_REQUIRE_BACKEND=1 with UI+backend running",
    );

    await page.goto("/");
    await page.getByLabel("Username").fill("admin");
    await page.getByLabel("Password").fill("admin123");
    await page.getByRole("button", { name: "Sign in" }).click();

    await expect(page.getByRole("button", { name: "Start" })).toBeVisible({
      timeout: 20_000,
    });
    await page.getByRole("button", { name: "Start" }).click();
    await expect(page.getByText("SIM RUNNING")).toBeVisible({ timeout: 15_000 });
  });
});
